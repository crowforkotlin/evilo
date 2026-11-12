# Application architecture and framework guide

Read this guide for shared features, framework integration, dependency wiring,
state, network/images, persistence, and resource lifetime. Paths are relative to
the evilo repository root. For module/source-set/build changes, use
[module-guide.md](module-guide.md). Configured dependency versions are owned by
`gradle/libs.versions.toml`, not this guide or individual module scripts.

## Framework owners

| Framework | Existing implementation | Constraint for changes |
| --- | --- | --- |
| Kotlin Multiplatform | `evilo-build-logic/base`, module `build.gradle.kts` files | Shared app features stay in `commonMain`; platform launchers supply entry points and real platform dependencies. |
| Compose Multiplatform / Material Design 3 | `evilo-app/src/commonMain/kotlin/crow/evilo/ui`, `feature`, and `composeResources` | Reuse `EviloTheme`, `EviloLayout`, shared components, localized resources, and the bundled ChillRoundM font. |
| MVI / kotlinx.coroutines | `evilo-core/src/commonMain/kotlin/crow/evilo/architecture/MviStore.kt`, `LatestOperation.kt`, core feature stores/controllers | Observe immutable `StateFlow`, send typed intents on the owning UI dispatcher, and preserve cancellation and replacement cleanup. |
| Koin | `evilo-app/src/commonMain/kotlin/crow/evilo/app/AppEnvironment.kt`, core/UI `EviloCoreModule.kt` and `EviloUiModule.kt` | Use the app's isolated `koinApplication`; do not start global Koin in launchers or pages. Reader controllers are parameterized factories, not singletons. |
| Ktor | `evilo-core/src/commonMain/kotlin/crow/evilo/network/ktor/BoundedHttpTransport.kt`, `PluginNetworkPolicy.kt` | Reuse one bounded transport and approved URL/origin/method/header policies; no automatic redirects, cookies, or ambient credentials. |
| Coil | `evilo-app/src/commonMain/kotlin/crow/evilo/app/EviloImages.kt`, `PluginImageDecoder.kt`, `ui/EviloAsyncImage.kt` | Use approved catalog/session request models and byte fetching, not unrestricted URL loading; preserve pre-decode limits and session-scoped cache invalidation. |
| kotlinx.serialization | `evilo-library/src/commonMain/kotlin/crow/evilo/plugin/api/PluginProtocol.kt`, core `storage/EviloPreferences.kt` | Keep JSON protocol/catalog models separate from Protobuf settings. Unknown optional JSON fields remain tolerated; format/protocol compatibility still applies. |
| DataStore / Okio | `evilo-core/src/commonMain/kotlin/crow/evilo/storage/EviloPreferencesRepository.kt`, app `AppStorage.kt` and platform bindings | Store settings/plugin KV as typed Protobuf. Guest callbacks access staged memory; host operations commit asynchronously. Do not reset corrupted data or fall back to memory. |
| Room3 / KSP / SQLite | `evilo-core/src/commonMain/kotlin/crow/evilo/storage/EviloDatabase.kt`, `evilo-core/build.gradle.kts`, app platform storage bindings | Catalog subscriptions/cache/installations/floors/deletion records use the transactional DAO. Preserve decimal-text version codes for Web numeric transport. |
| Wasmline | `evilo-core/src/commonMain/kotlin/crow/evilo/app/PluginSessionFactory.kt`, `plugin/runtime/WasmlinePluginLoader.kt`, SDK service interfaces | Load signed remote manifests using app-supplied keys. Keep guest services synchronous and portable; catalogs do not provide trusted keys. |

The shared dependency direction is `evilo-app -> evilo-core -> evilo-library`.
Core returns models and image bytes, never Compose types. Provider code and
release artifacts belong in independent repositories, not app dependencies.

## Startup and dependencies

`App()` calls `rememberAppStorageFactory()` and `AppEnvironment.create()`.
The environment opens storage, creates the host descriptor/generated public-key
lookup, and registers `eviloCoreModule()` plus `eviloUiModule()`. It then calls
`PluginCatalogRepository.initialize(BundledPluginCatalogJson)`. Non-preview
startup dispatches `PluginHomeIntent.Refresh`; previews explicitly use memory
stores. `KoinIsolatedContext` exposes the environment to Compose.

Normal shutdown closes the home store, connection controller, Coil, HTTP
transport, app scope, storage, and Koin in that order. `closeResources()` attempts
all closes and preserves suppressed failures. Startup retry waits for the prior
environment to close before opening the same DataStore files.

## Feature and installation changes

MVI means Compose pages observe state and send typed intents; stores own actions
and publish new state. Feature edits normally change a core state/intent/store,
its Koin registration, and its Compose route, not a new Gradle module. Unsubmitted
form text and local version selection may stay in Compose state.

- `PluginHomeStore` combines catalog/connection state and owns Browse/More,
  the typed `HomePage` stack, local/source filters, display projections, errors,
  and one `HomeOverlay`. Sources/settings/plugin details are pages; only temporary
  selectors and permission/removal/uninstall confirmations are overlays. Refresh
  has an independent operation scope and does not block local search/navigation.
- `PluginHomeIntent.ApproveInstall` reaches `AppPluginController.install()` via
  `PluginHomeStore.dispatch()`. The controller checks the release, acquires it
  through `PluginSessionFactory.connect()` and `WasmlinePluginLoader.loadRemote()`,
  then records the verified descriptor with `PluginCatalogRepository.recordInstallation()`.
  Install-only closes the verification session and keeps the initiating page;
  install-and-browse reuses that session. Each open still fetches/verifies the signed
  manifest with a zero TTL; the default Wasmline artifact cache reuses hash-checked
  modules. Debug builds retain observable phase timings in `PluginConnectionState`,
  while release UI shows generic progress. Keep acceptance checks before publishing
  active sessions.
- Installed Browse rows call `AppPluginController.open()`; their independent Info
  action opens full details without connecting a session. Source catalogs own
  All/Installed filters and first-install review. `BrowseProjection.kt` merges
  display only; all mutations keep `sourceId:pluginId`. Saved defaults or oldest
  installations determine source choice, never mirror version; unavailable
  defaults require explicit selection. Single-source filtering does not save a
  new default. Preserve timestamps for unchanged releases and conservative NSFW
  classifications across installed copies.
- `MediaBrowserController` handles typed search/detail/chapter/pagination intents.
  Its Koin factory receives the session and selected `MediaKind`; the Compose
  route closes the controller and image scope on disposal. Nested Back retains
  catalog results/query and saveable list positions rather than searching again.
  `EviloPredictiveBackHandler` seeks shared page transitions using render-only back
  snapshots; only gesture completion dispatches Back. Cancellation retains the
  page/session, stale navigation keys cannot pop replacement pages, and root home
  Back remains available to the operating system. Keep the handler callback stable.
- `LatestOperation` cancels obsolete jobs and serializes replacements with a
  mutex. Keep its generation checks so older callbacks cannot overwrite the
  newer operation's error or busy state. Returning from Add Source/source selection
  cancels obsolete navigation-producing work; completed persistence still publishes.

## Catalog editing and parsing

`plugin-repository/plugin.json` is the default editable catalog.
`GenerateBundledPluginCatalog` in `evilo-build-logic/app` emits the Kotlin data
consumed by `AppEnvironment`. `catalogFile` in `evilo-app/build.gradle.kts` and
`DEFAULT_BUNDLED_CATALOG_URL` select the bundled file and refresh URL.
Other subscriptions may use any filename or no `.json` suffix: their response
must pass `CatalogParser`; publishers need no PR to evilo or external schema file.
`CatalogParser.parse()` reads a JSON object, calls `normalizeCatalogJson()` in
`CatalogJson.kt`, decodes `PluginCatalog`, and applies existing cross-field rules.
Compact input inherits common plugin metadata into releases and derives omitted
summary dates/version without using the clock. Explicit overrides are whole-field
replacements, not merges; stored metadata remains full and release-specific.

Keep catalog status separate from trusted keys and user consent. There is no
review/certification field or filter, and independent sources cannot add signing
keys. All mandatory version floors, including the built-in source's floors, apply
only to their own subscription. Preserve existing Room records when changing
source IDs/URLs and preserve recorded permissions when catalog defaults change.

## HTTP, images, and persistence

Plugin HTTP request construction/parsing uses synchronous Wasmline services;
`WasmlineMediaSource` performs asynchronous host HTTP between service calls.
Keep explicit operation/continuation context, release permissions, bounded
responses, and cancellation. Do not add suspending bridge methods to accommodate
Ktor or DataStore.

`EviloImages` fetches catalog icons through Ktor and media through
`MediaSourceSession.imageBytes()`. Approval precedes cache lookup and returning
results. `SessionImages.close()` cancels its supervised requests, publishes
disconnection, and evicts its cache entries. Blank URLs have no request; retries
bypass cache reads. `validatePluginImage()` checks PNG/JPEG/WebP dimensions before
decoding; `PluginImageDecoderFactory` classifies platform decoder failures without
converting cancellation/limits. Typed `ImageFailure` diagnostics omit exception
messages, header values, credentials, paths, queries, and fragments. Debug-only
image details retain the typed policy rejection and a validated header name. Keep icon/cover/page
states separate and single-image retry; cover-header SDK changes need runtime evidence.
Video playback is external and cannot forward plugin headers; in-app video,
library/history/downloads, saved progress, and synchronization are not implemented.

DataStore owns `evilo.preferences.pb`; Room3 owns `evilo.catalogs.db`.
`PluginStorageSession.transaction()` stages guest KV per signed plugin ID and
commits successful changes with `DataStore.updateData()`. Versions/subscriptions
of one signed ID share KV. Room commits finish before repository state publishes.
Room and DataStore do not share a transaction: last-installation uninstall records
pending KV deletion in Room, then erases DataStore; startup/refresh/install retries
unfinished deletion. Source removal retains KV.
New duplicate/default preferences use Protobuf fields 5/6; existing fields are not
renumbered. Pin duplicate defaults before destructive source changes or release
replacement so a new installation timestamp cannot select another source; preserve a
removed default while another installation exists and prune only IDs with no copy.

Android uses private files, JVM uses app data, and iOS uses Application Support.
JS/Wasm JS use `WebOpfsStorage` and `WebWorkerSQLiteDriver`. The module worker in
`evilo-web/src/webMain/resources/sqlite-worker/worker.js` owns persistent SQLite
through an OPFS SAH pool and a lifetime Web Lock. Preserve its ownership/shutdown
protocol and full deployed SQLite assets; unsupported storage reports an error,
not a localStorage or memory fallback. Browser CORS, mixed-content restrictions,
quotas, and eviction still apply.

## Detailed references

- [Application architecture](../../../../docs/application-architecture.md): dependency lifetime, MVI usage, DataStore/Room semantics, platform bindings, and Web Worker deployment.
- [Design system](../../../../docs/design-system.md): shared MD3 colors/font/components, breakpoints, and interaction rules.
- [Plugin architecture](../../../../docs/plugin-architecture.md): synchronous protocol, signed loading, permissions, and runtime limits.
- [Catalog format](../../../../docs/plugin-catalog.md): independent hosting, compact fields/inheritance, releases, and source-scoped updates.
- [Manual commands](../../../../README.md#manual-commands): platform prerequisites and commands to suggest, not automatic execution permission.
- [Browse/source/image progress](../../../../docs/browse-plugin-plan-progress.md): requirement ownership, test sources, and outstanding runtime/validation evidence.
