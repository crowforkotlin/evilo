# Application architecture

## Module ownership

The application has exactly three shared modules. Features are packages, not
additional Gradle projects:

| Module | Responsibilities | Project dependency |
| --- | --- | --- |
| `evilo-app` | Compose pages, MD3 theme/font, Coil, resources, app composition, platform storage bindings | `evilo-core` |
| `evilo-core` | MVI stores, catalogs, HTTP policy/transport, persistence contracts, signed loading, media sessions | `evilo-library` |
| `evilo-library` | Portable media models, JSON envelopes, synchronous Wasmline services, legacy manga adapter | None |

`evilo-android`, `evilo-desktop`, `evilo-web`, and the Xcode-only `evilo-ios` are
launchers. Shared modules retain Android, JVM, iOS ARM64/simulator ARM64, JS, and
Wasm JS; only `evilo-library` also targets WASI. Host storage, HTTP clients, and
Compose must not enter guest modules. Real providers belong in independent
`evilo-plugin-<provider>` repositories.

Dependency versions are declared once in `gradle/libs.versions.toml`. See the
[README framework table](../README.md#frameworks-and-application-architecture)
for the configured versions, including the requested Room3 alpha and Koin beta.

## Entry point and dependency lifetime

Every launcher uses `crow.evilo.app.App()`:

```kotlin
import androidx.compose.runtime.Composable
import crow.evilo.app.App

@Composable
fun ApplicationContent() {
    App()
}
```

`App.kt` creates platform storage through `rememberAppStorageFactory()`, then
calls the internal `AppEnvironment.create()`. `AppEnvironment.kt` owns one
isolated `koinApplication`; `KoinIsolatedContext` exposes it to Compose. Do not
call global `startKoin()` from platform launchers or pages.

`eviloCoreModule()` supplies the host descriptor, app-controlled trusted keys,
typed DataStore, catalog DAO, one Ktor client/transport, preferences repository,
catalog repository, session factory, connection controller, and home store.
`eviloUiModule()` supplies one `EviloImages` instance and Coil loader.
`MediaBrowserController` is a Koin factory parameterized by session and media
kind, not an application singleton.

Initialization loads Room records, retries pending plugin-data deletions, reads
preferences, and initializes the bundled plugin catalog. Non-preview startup
then dispatches `PluginHomeIntent.Refresh`. Compose previews use explicit memory
stores; production startup never silently switches to them after an error.
Startup failures show their message and a retry action.

One supervisor scope belongs to the environment. Normal disposal attempts every
step in order: home store, connection controller, Coil, HTTP transport, app scope,
DataStore/Room/backend, and Koin. `closeResources()` preserves later failures as
suppressed exceptions rather than skipping the remaining resources. A startup
retry waits for the previous environment to finish closing before opening the
same DataStore files.

## MVI conventions and usage

MVI means pages observe state and send typed intents; stores perform operations
and publish new state. The common `MviStore<State, Intent>` exposes
`StateFlow<State>`, `dispatch(intent)`, and suspending `close()`.

For example, the existing home entry resolves its store from Koin:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import crow.evilo.feature.browse.PluginHomeIntent
import crow.evilo.feature.browse.PluginHomeStore
import org.koin.compose.koinInject

@Composable
fun CatalogRefreshAction() {
    val store = koinInject<PluginHomeStore>()
    val state by store.state.collectAsState()
    Button(
        enabled = !state.catalog.refreshing,
        onClick = { store.dispatch(PluginHomeIntent.Refresh) },
    ) {
        Text("Refresh (${state.catalog.subscriptions.count { it.enabled }} sources)")
    }
}
```

Production labels use Compose string resources. Pages must not create
repositories, clients, databases, or private coroutine scopes for these actions.
Local unsubmitted form text and version selection may remain Compose state.

- `PluginHomeStore` owns the Browse/More destination, typed `HomePage` return stack,
  local/source search and filtering, display projections/counts, mutation status,
  and one `HomeOverlay`. Sources/settings/details are pages; temporary selectors
  and permission/source-removal/uninstall confirmations replace one another.
  Catalog checks have a separate operation scope, so local search and navigation
  remain usable while refresh runs. Returning from Add Source or source selection
  cancels obsolete navigation-producing work, not unrelated preference commits.
- `AppPluginController` owns installing/opening/closing signed media sessions and
  reports precheck, package verification, negotiation, and installation-recording
  phases. Approval rechecks the exact reviewed release against the current catalog.
  Install-only records then closes the verification session; install-and-browse
  retains it. Explicit source persistence is part of the cancellable open operation,
  with installation/kind/blocking checks before an active session is published.
- `MediaBrowserController` receives `MediaBrowserIntent` for search, pagination,
  details, chapters, retry, and back. Its route closes the controller/session and
  image scope when it leaves composition. `MediaBrowserState.backDestination()`
  supplies the same previous-page snapshot to both preview and committed Back.
  Shared `EviloPredictiveBackHandler` retains progress, swipe edge, and vertical
  touch movement in portable `EviloBackEvent` values. It mounts a previous page
  through `SeekableTransitionState`, keeps both pages alive, and transforms the
  full outgoing page with Flutter-style predictive geometry, separately from the
  ordinary page slide/fade. It does not dispatch
  intents while swiping; cancellation restores the page and completion commits
  Back only if the navigation key still matches. Catalog Back delegates to the
  home handler, and the root does not intercept system exit.
- `LatestOperation` cancels obsolete jobs and waits for their operation mutex
  before replacements execute. Generation checks prevent old failure/finalization
  callbacks from changing the new operation's state. Explicit `cancel()` preserves
  cleanup ordering while suppressing obsolete return/error callbacks.

Call `dispatch()` on the application's UI dispatcher. Controllers inherit that
dispatcher; their lifecycle fields are not a general concurrent API. Network,
Wasmline operations, and decoding move to their owning background implementations,
not by moving entire store actions off the UI dispatcher. Preserve cancellation
and close acquired sessions if negotiation or installation fails.

For new features, add a state/intent/store package in `evilo-core`, register it
in `eviloCoreModule()`, and add its Compose route in `evilo-app`. Reuse
`EviloTheme`, layout constants, components, and `EviloAsyncImage`; do not add a
feature module or duplicate platform pages. The component rules are in
[design-system.md](design-system.md).

## Installed Browse and source management

`BrowseProjection.kt` computes display-only rows from source-qualified installation
records. It checks installed-release kinds, enabled sources, conservative installed
NSFW classifications, the explicit source filter, and duplicate preferences before
matching the localized displayed name/source/tags. Search does not choose another
source. All-sources merging uses a saved installation key or the chronological
oldest installation with subscription order as a tie-breaker. An unavailable
default produces a source-selection row rather than automatic fallback.

Every open/update/uninstall still uses `sourceId:pluginId`; raw installation
management never merges records or limits them to the selected media kind.
Source catalogs retain All/Installed filtering and show uninstalled releases.
`refresh(sourceId)` records independent source results and keeps failed-source
caches without relabeling other successful checks. `lastSuccessAt` is session
state, separate from the publisher's catalog date; no Room column was added.

Compose's saveable state holders retain owning-page list/grid positions and local
input. Media details/back keep catalog results in `MediaBrowserController` rather
than executing another search. Media-route state belongs to its session; closing
the browser removes its saved page state so a newly opened empty-query catalog
cannot restore text/results from an old session. App Bar/system/Escape return handling closes the
current confirmation or nested reader page before leaving the works session.
Source removal, uninstall, media-kind changes, and newly hidden NSFW routes cannot
retain an active browser for an invalid installation.

## HTTP and image requests

`BoundedHttpTransport` owns the application Ktor client. Native clients use
OkHttp on Android/JVM and Darwin on iOS; browser clients use the JS engine.
Catalog, package, plugin API, and image requests all pass through bounded
transport and their corresponding URL/permission policies. Requests reject
redirects and implicit credentials. Signatures are not network authorization.

`EviloImages` configures Coil with permission-aware request models, a custom
fetcher, and `PluginImageDecoderFactory`. Plugin images call `MediaSourceSession.imageBytes()`; catalog icons call
the same Ktor transport. Pages do not pass arbitrary URL strings to an automatic
Coil network component. Service-loader components and disk/weak-reference caching
are disabled; the shared memory cache is limited to 32 MiB.

An interceptor approves the URL/session before cache access and again before
returning a result. Cache keys hash approved URL/headers and use separate catalog
and per-session prefixes. `SessionImages.close()` immediately invalidates the
session's image scope, cancels supervised in-flight requests, publishes disconnection,
and evicts its entries. Retry attempts bypass memory-cache reads without changing
the approved URL/header key; blank URLs have no request. JPEG/PNG/WebP headers and dimensions
are checked before Coil decodes: icons have 1 MiB/512/262,144-pixel limits; media
has 16 MiB/16,384/33,554,432-pixel limits. Remote SVG is unsupported.

Platform decoder failures after header validation become typed format errors;
cancellation and limits retain their types. `NetworkFailures.kt` and
`ImageFailure.kt` preserve actual HTTP statuses/limits and distinguish policy,
network, redirect, format, and session failures without exception-string matching.
`EviloAsyncImage` separates icon/cover/reader presentations. Its diagnostic dialog
omits exception messages and all request data except a redacted HTTP(S) origin;
copy uses only those safe fields. Cover headers still use an empty map. Any SDK
cover-header extension requires a real failure sample and a protocol decision.

## DataStore and synchronous plugin KV

`EviloPreferences` is a typed Protobuf model. `EviloPreferencesCodec` validates
schema version 1, nonnegative namespace revisions, and bounded preferred-installation
IDs/keys, and limits the serialized
file to 32 MiB. Malformed or unsupported data raises `CorruptionException`; there
is no automatic reset handler. `EviloPreferencesRepository` supplies selected
media kind, NSFW and duplicate-display preferences, default installation choices,
and plugin storage sessions. Existing Protobuf fields 1..4 are unchanged;
`mergeDuplicatePlugins` and `preferredInstallations` use fields 5 and 6 with
deterministic defaults. Failed preference writes leave the published value unchanged.

Wasmline services stay synchronous. Do not add `suspend`, overloads, generics, or
new bridge signatures to make DataStore calls from a guest. The existing host
service methods operate on staged memory:

1. `PluginStorageSession.transaction()` acquires the shared mutex for the
   host-selected publisher/plugin storage key and reads its durable values from
   DataStore. `PluginSessionFactory` supplies that key through
   `EviloPreferencesRepository.pluginSession(pluginId, storageKey)`.
2. The guest's `readValue`, `writeValue`, `removeValue`, and `listKeys` access only
   that staged namespace while the operation is active. The host applies consent
   and per-plugin key/value/count/total limits.
3. After the host operation succeeds, changed values commit through suspending
   `DataStore.updateData`. The update checks the initial namespace/revision to
   reject another app instance's conflicting write.
4. Operation failure or cancellation before commit discards staged changes. Once
   commit starts, it finishes under `NonCancellable` while retaining the mutex;
   cancellation cannot undo a completed durable write. Write failures propagate.

`writeValue(true)` acknowledges staged acceptance, not immediate disk durability.
Different versions/subscriptions of one verified publisher/plugin ID share data.
Official releases retain the legacy ID-only persistent key; third-party storage
uses `publisher:<public-key fingerprint>:<plugin ID>`. Guests still access only
`plugin.<plugin ID>` and cannot select the persistent key or read another
publisher's retained values. Erasing values keeps an empty persistent namespace
with an incremented revision, so stale operations cannot reuse an old
revision. Runtime disposal waits for operations but never reads DataStore or
permits guest KV callbacks. The protocol comments live in
`evilo-library/src/commonMain/kotlin/crow/evilo/plugin/api/PluginProtocol.kt`.

## Room3 catalog records

`EviloDatabase.kt` defines a version-1 Room3 database and transactional
`RoomPluginCatalogStore` DAO. It stores subscriptions, cached catalog JSON,
installation metadata, mandatory version floors, and pending plugin-data
deletions. Version codes are indexed as decimal text because the current SQLite
Web driver transports numbers through JavaScript doubles; installation JSON is
checked against its indexed identity and version.

`PluginCatalogRepository.initialize()` renames the previous built-in source to
`bundled` and updates its default URL, cache key/display metadata, installation
source fields, and scoped floor keys within the existing Room records. The
installation release/time, disabled state, and plugin KV are retained. This is
not a migration from the removed JSON/localStorage storage implementations.

`PluginCatalogRepository` validates catalog/release metadata before mutations.
Room writes complete before new repository state is published. Once a mutation's
commit begins, cancellation cannot interrupt the commit/publication pair and
leave the live state behind durable records. An identical installed release
retains its original record and timestamp.

Before disabling/removing/uninstalling or replacing the release of an implicit
duplicate default, the repository
pins that installation key in DataStore so display cannot silently move to another
source. A replacement's new installation timestamp cannot change that default.
Removed-source preference tombstones remain while another installed copy
exists, requiring explicit selection; preferences are pruned only when no copy of
that signed ID remains. This does not alter Room installation keys or merge floors.

Room and DataStore do not share a transaction. Last-installation uninstall first
atomically removes the installation and records pending KV deletion in Room,
then erases DataStore values and clears the marker. Startup, refresh, and
installation preparation retry unfinished deletion. Removing a JSON subscription
removes its catalog/installation records but retains plugin KV.

KSP generates `EviloDatabaseConstructor` and DAO/database implementations for
Android, JVM, both iOS targets, JS, and Wasm JS. `evilo-core/build.gradle.kts`
uses the `androidx.room3` plugin and exports schemas to `evilo-core/schemas`.
Schema JSON has not been generated for this implementation; do not invent a
schema file or claim generation succeeded without a requested build.

This app is unreleased. Old snapshot JSON and localStorage implementations are
removed without migration or legacy-format compatibility code.

## Platform storage

| Platform | DataStore | Room3 |
| --- | --- | --- |
| Android | `OkioStorage`, app-private `evilo.preferences.pb` | App-private `evilo.catalogs.db`, `BundledSQLiteDriver` |
| JVM | `OkioStorage`, Windows APPDATA/macOS Application Support/XDG app data | Same app-data directory, `BundledSQLiteDriver` |
| iOS | `OkioStorage`, Application Support/evilo | Same directory, `BundledSQLiteDriver` |
| JS / Wasm JS | `WebOpfsStorage`, `evilo.preferences.pb` | `WebWorkerSQLiteDriver`, persistent SQLite SAH pool in OPFS |

`AppStorage` owns the DataStore job, Room database, and browser worker when
present. Closing is serialized/idempotent and waits for the DataStore job before
closing the database/backend. Native multi-process and cross-device
synchronization are not implemented.

## Browser worker and deployment

[Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
document JS/Wasm JS support and `androidx.sqlite:sqlite-web`. Room3 does not ship
a default SQLite worker; `evilo-web` supplies it without adding another module.

`evilo-web/src/webMain/resources/sqlite-worker/worker.js` implements the AndroidX
`open`, `prepare`, `step`, and one-way `close` message protocol. It serializes
commands, resets bindings, buffers rows, checks numeric transport limits, and
closes statements/databases before releasing storage ownership.

The worker loads `@sqlite.org/sqlite-wasm` and installs the `evilo-opfs` SAH pool
in `/evilo-sqlite`, with initial capacity 6 and no clear-on-init behavior. It
preserves existing files on initialization failure. One lifetime Web Lock,
`evilo-sqlite-owner`, prevents another evilo tab on the same origin from opening
a second pool over these files. The app waits for startup/shutdown replies, with
30-second/10-second timeouts respectively; final shutdown terminates the worker.
Room uses TRUNCATE journaling and one Web database connection. This SAH-pool
configuration does not require SharedArrayBuffer or cross-origin isolation.

The web build declares the npm version in the catalog. Separate JS/Wasm copy
tasks depend on their npm installation and add `index.mjs`, `sqlite3.wasm`, and
`sqlite3-opfs-async-proxy.js` to generated resources. After a requested distribution
build, deploy the complete output, including this relative asset layout:

```text
index.html
app.js
sqlite-worker/
  worker.js
  NOTICE.txt
  APACHE-2.0.txt
  sqlite3/
    index.mjs
    sqlite3.wasm
    sqlite3-opfs-async-proxy.js
```

The npm package requires Node.js 22 or newer at build time. Serve the complete
distribution over HTTPS or localhost with JavaScript/Wasm MIME types and a CSP
that permits same-origin module workers and WebAssembly compilation. Workers
resolve relative to `document.baseURI`; do not deploy only `app.js` or rely on a
CDN URL for the database runtime. The worker notices include the npm license and
upstream SQLite/Emscripten provenance; copied runtime headers must remain intact.

Production storage requires OPFS, Web Workers, and Web Locks. Unsupported/private
browser modes report startup failures, not an in-memory/localStorage fallback.
Close the other evilo tab before retrying an ownership failure. Browser quotas,
eviction, CORS, and mixed-content restrictions still apply. A worker or Wasm
module cannot bypass them.

## Implementation status

These are source/configuration and API usage rules, not execution results.
Dependency resolution, schema generation, APK/desktop/web/iOS compilation,
installation, rendering, and runtime persistence have not been verified for
these changes. Test sources cover persistence failures, staged KV cancellation
and serialization, pending-deletion retry, same-release records, and existing
catalog/network/controller behavior; they have not been run. Manual commands and
platform prerequisites are in [README.md](../README.md#manual-commands).
