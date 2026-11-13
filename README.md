# evilo

evilo is a Kotlin Multiplatform media-reader application with one Compose
Material Design 3 UI for Android, desktop, browsers, and iOS. Providers are signed
Wasmline plugins loaded from JSON catalogs, not app dependencies or APK extensions.

## Repositories

| Repository | Responsibility |
| --- | --- |
| `https://github.com/crowforkotlin/evilo` | Host app, maintained plugin SDK, bundled JSON catalog |
| `https://github.com/crowforkotlin/evilo-plugin-templatea` | Provider-neutral multi-module template |

The template's local directory remains `../evilo-plugin-template`; its remote
spelling is preserved. Third-party repositories use `evilo-plugin-<provider>`
and start from the template. The app and template contain no real provider
implementation. Keep host code, build configuration, resources, and documentation
provider-neutral; third-party implementations and their release files stay in
independent repositories.

## Modules

Root project: `evilo-root`. The app has three shared modules with prefixed
top-level names, explicit settings includes, and an included convention build.
UI features are packages in one module, not separate Gradle projects.

```text
evilo-app/                   All shared Compose UI, theme, fonts, and app composition
evilo-core/                  Catalogs, HTTP, storage, loading, sessions, and controllers
evilo-library/               Portable models and the maintained plugin protocol SDK
evilo-android/               Android launcher, manifest, resources, APK packaging
evilo-desktop/               Desktop launcher and distribution configuration
evilo-web/                   JS/Wasm JS viewport launcher and web loading assets
evilo-ios/                   Xcode/SwiftUI launcher; not a Gradle subproject
evilo-build-logic/
  base/                      KMP/Android conventions and shared property lookup
  app/                       Compose/desktop/web conventions and signing/catalog tasks
plugin-repository/           One editable bundled plugin.json
docs/                        Plugin contracts and shared UI design rules
```

Dependencies flow `evilo-app -> evilo-core -> evilo-library`. Only the library is
published for plugins; it has no Compose, host HTTP, or persistence backend.
Existing Kotlin packages are preserved: models/protocol use `crow.evilo.model`
and `crow.evilo.plugin.api`; host logic uses `crow.evilo.plugin.*`,
`crow.evilo.network.ktor`, `crow.evilo.storage`, and app/reader controller packages.
UI uses `crow.evilo.app`, `crow.evilo.ui`, and `crow.evilo.feature.*` inside
`evilo-app`. Platform launchers do not own business UI.

`evilo-app` supplies `App()` for every platform. Android calls it from
`MainActivity`, desktop from `MainKt`, web through `WebApp()`, and iOS through
`MainViewController`. `evilo-app` owns the teal light/dark palettes, the supplied
ChillRoundM SemiBold font, typography, rounded shapes, and width limits. Compact
windows use Browse/More with a centered 64 dp circular content-selector FAB; at
600 dp the same navigation moves to a rail. Page content is capped at 1200 dp
and reading at 900 dp. Cards, search/filter controls, empty states, grouped
settings, source/detail pages, and confirmation dialogs share the same rules without
platform-specific page copies. Colors, font sizes, spacing, radii, elevation,
responsive behavior, and the font license are documented in
[`docs/design-system.md`](docs/design-system.md).

Shared modules retain Android, JVM, iOS ARM64/simulator ARM64, JS, and Wasm JS.
Only `evilo-library` also declares Wasm WASI for guests.
`evilo-desktop` and `evilo-web` own executables; `evilo-app` exports the static
`Evilo` Apple framework. Apple builds require macOS/Xcode, not target removal.

## Frameworks and application architecture

Versions are declared once in `gradle/libs.versions.toml`:

| Responsibility | Framework | Configured version |
| --- | --- | --- |
| Image requests, memory cache, decoding, Compose rendering | Coil | `3.6.3` |
| All host HTTP transport | Ktor | `3.6.1` |
| Signed plugin loading and synchronous services | Wasmline | `1.0.0-beta1` |
| Settings and plugin key/value data | DataStore | `1.3.0` |
| Catalog subscriptions, caches, installation records, update floors | Room3 | `3.1.0-alpha03` |
| Room DAO/database generation | KSP | `2.3.13` |
| Catalog/protocol JSON and preferences Protobuf | kotlinx.serialization | `1.12.0` |
| Application dependencies and parameterized reader controllers | Koin | `4.3.0-Beta1` |

Kotlin is `2.4.10` to satisfy Coil `3.6.3`'s published Kotlin requirement.
Room3 and Koin intentionally use the requested alpha/beta versions; configuration
is not a claim of successful dependency resolution or platform compilation.

`App()` owns an isolated Koin application. Shared MVI stores expose immutable
`StateFlow` state and accept typed `dispatch(intent)` calls. Compose pages do not
construct repositories, HTTP clients, or platform stores. The app has one Ktor
transport and one Coil loader; plugin image requests use custom permission-aware
Coil components, not unrestricted URL loading. See
[`docs/application-architecture.md`](docs/application-architecture.md) for entry
points, store usage, disposal, storage semantics, and browser setup.

## Current behavior and limits

Browse is the installed-plugin entry point, not a catalog store. It shows local
search, optional source filtering, and installed plugins grouped by source.
Rows contain an icon, name, compact labels, and a separate Info action. The row
opens the installed plugin's works catalog directly; Info opens plugin details
without connecting a works session. Blocked installations remain manageable.
Disabled sources retain their records but cannot be opened and are hidden in Browse.

The App Bar's Plugin Sources action opens source management. Each source has its
own All/Installed catalog, search, enabled state, check results, and read-only URL
settings. Installable, uninstalled catalog entries appear there, not in Browse.
More contains Settings, Plugin Sources, Safety, and About. Settings groups Browse
and content, Plugins and sources, Images, Appearance, Security, and About; raw
installation management includes disabled sources and every installed copy.
NSFW is hidden by default. Appearance and font rows describe the existing system
theme and bundled ChillRoundM font, not unimplemented appearance switches.

Duplicate merging defaults on and affects display only. Installed copies are
grouped by signed plugin ID while every action retains `sourceId:pluginId`.
The default source is an explicit saved choice or the oldest installation, never
the mirror with the highest version. An unavailable default requires explicit
source selection; search and single-source filtering do not change the default.
Version floors and approved permissions remain source-specific. The strictest
known NSFW classification among installed sources applies to all copies.

Installation requires explicit permission review. Install stays on the management
page and closes the verification session; Install and browse reuses that verified
session for the works catalog. Opening still fetches and verifies a signed remote
manifest, but content-addressed modules can reuse Wasmline's hash-checked artifact
cache (128 MiB configured limit). Installation does not imply offline availability. The same installed release
retains its original record and timestamp. Returning or replacing a load cancels
obsolete work and closes acquired handles before another session is accepted.

Predictive Back previews Reader -> details -> works catalog -> previous home page
using full-page scale/rounded corners/edge-aware movement matching the default
Android predictive geometry in Flutter 3.44.2. App bars participate and the previous
page stays still underneath. Completion restores the scale while fading out;
ordinary button navigation keeps its slide/fade. Cancellation retains the current navigation
state; completion dispatches the existing Back action. Search and saveable list
positions remain attached to their page keys. Android explicitly enables system
predictive Back; the root home page does not intercept back-to-home. Gesture
rendering/device behavior has not been exercised for this change.

Images distinguish missing URLs, loading, HTTP/network failure, permission denial,
format failure, size limits, and disconnected sessions. Covers use compact states;
reader pages retain their page labels and individual retry/details actions.
Diagnostics omit original exception text, request header values, credentials,
paths, queries, and fragments. Debug image details additionally identify the typed
host policy rejection and a validated rejected header name, without exposing its
value. Live image requests have not been reproduced in this workspace.

Android diagnostics follow the installed application's debuggable flag; iOS uses
`Platform.isDebugBinary`. Desktop `:evilo-desktop:run` enables the development-only
`evilo.debugDiagnostics` JVM property; packaged applications default to disabled.
Browser launchers default to disabled and can explicitly use `WebApp(debugDiagnostics = true)`.
Debug shows observed startup phase durations, retains them after opening via the
browser toolbar's Info action, and exposes installation timings in plugin details.
Release shows generic loading text and no timing controls. The signed loader does
not expose individual crypto/runtime timestamps, so cached-module checks and
runtime creation may share one measured phase.

Users do not enter Ed25519 keys, key IDs, or individual WLM URLs. Installations
require a compatible release, matching signed source provenance, the app authority
signature, and explicit permission approval. A valid signature does not prove safety.
Other authors may maintain JSON catalogs, but cannot supply trusted keys. Catalogs
use the same release-signature and permission requirements regardless of source.

Image/text reading code is shared. Video URLs open externally after approval;
custom playback headers are unsupported. Library/history, saved reading progress,
downloads, cloud sync, login, and in-app video are not implemented.

The bundled `plugin-repository/plugin.json` remains empty until actual signed
packages and matching source archives are published. The default refresh
URL is `https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/plugin.json`.
Pushing application or plugin source code does not publish an installable plugin
package. SDK publication is configured, not established remotely.

## Install a plugin

The app installs releases from a published JSON catalog. A source repository
homepage, individual `.wlm` or `.wasm` file, and signing key are not subscription
addresses.

1. Open Browse, tap the App Bar's Plugin Sources action, then Add plugin source.
2. Enter the publisher's direct JSON catalog URL and tap Subscribe. Prefer HTTPS;
   plain HTTP requires explicit cleartext consent. This downloads metadata only,
   without installing or executing the listed plugins.
3. Open the source's catalog and select a supported content type. NSFW entries
   remain hidden unless enabled in More -> Settings -> Browse and content.
4. Open a plugin's details, select a compatible release, review its actual source
   links and permissions, and approve installation. Install keeps this page;
   Install and browse explicitly enters the works catalog after verification.
5. Tap its installed Browse row to enter works directly. The separate Info button
   manages versions, updates, permissions, source copies, and per-source uninstall.
   No WLM URL, key ID, or Ed25519 key is entered by the user.

The built-in source is already subscribed and can be disabled. An empty
`plugin.json` means there are no releases to install from it; refreshing an empty
list does not create a plugin. Catalog filenames are unrestricted: `plugin.json`,
`plugins.json`, another name, or an endpoint without a `.json` suffix is accepted
when its response satisfies the catalog format. The illustrative URLs in the
catalog documentation are not installable releases.

## Publish your own plugin source

Host your catalog independently; no PR to evilo's bundled `plugin.json` is needed.
Share its direct HTTP(S) URL for users to subscribe through Plugin Sources. The
smallest valid source is:

```json
{
  "name": "My plugins",
  "plugins": []
}
```

For a nonempty source, write each plugin's stable ID, name, authors, introduction,
kinds, languages, source repository, and common permissions once. Releases inherit the
common fields unless overridden. Author names may be strings, icons are optional,
dates accept `YYYY-MM-DD`, and current version/summary dates are derived. There
is no `$schema` association or separate schema file to maintain. See
[`docs/plugin-catalog.md`](docs/plugin-catalog.md) for the compact example and
updating old releases without changing their permissions.

The template's release exporter supplies a usable `plugins.json`, actual source
archive/hash, signed `manifest.wlm`, and content-addressed `artifacts/` directory.
Publish that output directly or copy its release metadata into your own catalog;
do not invent hashes or source commits. All downloads must be served without
redirects. Independent catalogs still require packages signed by the authority
embedded in the app; they cannot add their own trusted keys. Keep provider code
and artifacts outside evilo. See the template README for export commands.

## Configuration

| File | Owned values |
| --- | --- |
| `gradle/libs.versions.toml` | Dependency/compiler versions, SDK levels, JDK, SDK version |
| `project.properties` | App identity, release version/code, iOS deployment target |
| `gradle.properties` | Gradle/Kotlin execution configuration |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution/checksum |
| `evilo-ios/Configuration/Config.xcconfig` | Apple signing and shared property mappings |
| `plugin-repository/plugin.json` | Default bundled catalog, editable by publishers |

`project.properties` remains plain Java/Xcode-compatible `KEY=value` assignments.
Dependency versions are not duplicated in module scripts. `wasmline.localPath`
is an optional composite, never a committed absolute checkout path. It may
execute upstream tasks and requires that checkout's toolchain/artifacts.

## SDK, authority, and storage

The SDK coordinate is `io.github.crowforkotlin.evilo:evilo-library:1.0.0`.
Templates consume this library rather than copying models/protocols. Until
publication, use `-Pevilo.sdkPath=../evilo`. There is no third SDK repository;
changes are maintained once in `evilo-library`. Consolidation preserves the
serialized protocol and service packages; it does not change the bridge ABI.

`evilo-core` owns plugin connections and reader state without depending on Compose.
Sessions return permission-checked image bytes; `evilo-app` validates image headers
before Coil decodes them. The app supplies its generated public signing authority to
`PluginSessionFactory`; neither the core nor catalogs choose replacement keys.

`EviloPluginService` uses synchronous fixed JSON envelopes; `EviloHostService`
provides capabilities and private key/value storage. Required major/minor,
capabilities, and app version bounds are checked before loading. HTTP request
construction/parsing is separate from async host transport. Continuations carry
explicit context; service signatures do not change for optional fields. Legacy
manga API 2 remains an SDK adapter, not an installable API-2-only package format.

`:evilo-app:generatePluginSigningKeys` creates/preserves one external Ed25519
release-family authority, normally under `~/.evilo/signing`. App compilation
uses it and embeds only the public key. Keys are never overwritten; incomplete
or mismatched authority files require restoration. Templates check and reuse
this authority, not their own or old preview keys. A shared release family must
preserve the same authority across app updates, not generate one per phone.

Only the authority operator signs releases. Independent authors do not receive
private keys. Use the same `evilo.signingKeyDirectory` override across app/provider
builds. Back up keys outside repositories; never print, commit, archive, or upload
private material. Custom catalogs cannot change the trust authority.

Plugin storage is persistent and scoped by signed ID: default 1 MiB total,
256 entries, 64 KiB/value, and 128-character keys. It is not encrypted credential
storage. Synchronous guest writes stage values in the current host operation;
successful operations commit with DataStore's suspending `updateData`. Failure or
cancellation before commit discards staged values. Once commit starts, it retains
the operation mutex until persistence completes; cancellation cannot undo a
durable write. Storage failures are reported.

DataStore stores typed Protobuf in `evilo.preferences.pb`; Room3 stores catalog
records in `evilo.catalogs.db`. Android uses app-private storage, desktop uses app
data, and iOS uses Application Support. Both JS and Wasm JS use OPFS: DataStore's
`WebOpfsStorage` and a Room3 `WebWorkerSQLiteDriver` with the SQLite SAH pool VFS.
Browser storage requires HTTPS or localhost, OPFS, Web Workers, and Web Locks;
only one evilo tab per origin can own the database. Browser quotas and eviction
still apply. There is no cross-device synchronization or native multi-process
storage support. The previous JSON/localStorage persistence implementation is
removed; this unreleased app does not read or migrate that old format.

See `docs/plugin-architecture.md` for network policies and remaining runtime
limits, and `docs/plugin-catalog.md` for metadata, actual-source requirements,
compatible versions, and mandatory updates. The host checks signed revision/hash
consistency, not archive contents or reproducible builds. Current upstream does
not expose guest execution-fuel or linear-memory limits.

The requirement-to-source/test ledger is
[`docs/browse-plugin-plan-progress.md`](docs/browse-plugin-plan-progress.md).
It separates code and test sources from unexecuted tests, builds, visual checks,
and the unconfirmed image root cause.

## Manual commands

These are instructions, not execution records. Builds, tests, packaging, servers,
installation, screenshots, commits, and publication require their own explicit
requests. Authorizing an APK build/install does not authorize other targets or tests.

| Operation | Command from this repository |
| --- | --- |
| Android debug APK | `./gradlew :evilo-android:assembleDebug` |
| Preserve signing authority | `./gradlew :evilo-app:generatePluginSigningKeys` |
| Desktop compilation | `./gradlew :evilo-desktop:compileKotlin` |
| Desktop app | `./gradlew :evilo-desktop:run` |
| Browser Wasm distribution | `./gradlew :evilo-web:wasmJsBrowserDistribution` |
| Browser JS distribution | `./gradlew :evilo-web:jsBrowserDistribution` |
| iOS simulator framework | `./gradlew :evilo-app:linkDebugFrameworkIosSimulatorArm64` on macOS |
| Protocol/model tests | `./gradlew :evilo-library:jvmTest` |
| Catalog/storage/HTTP/runtime/controller tests | `./gradlew :evilo-core:jvmTest` |
| Image decoder, request approval, and image-scope tests | `./gradlew :evilo-app:jvmTest` |

Android needs the configured SDK and JDK. iOS uses
`evilo-ios/iosApp.xcodeproj`; its build phase invokes
`:evilo-app:embedAndSignAppleFrameworkForXcode`. Browsers obey CORS/mixed-content
restrictions; no server or proxy is started automatically.

The old `:app:android` task and preview signing properties are obsolete. Use the
independent plugin repository's README for signing/export. Do not publish stale
binaries, assign invented source commits/URLs, or describe unpublished packages
as installable releases.

## Project instructions

Project rules remain in `.agents/skills/evilo/`, with module/configuration and
architecture/framework guides. Comments and repository docs are English; user
replies use the user's language. Report source changes separately from observed
builds, installs, runtime behavior, and tests.
