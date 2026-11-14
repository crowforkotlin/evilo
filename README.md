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
Version floors and approved permissions remain source-specific. Same-ID copies
must use the same verified publisher identity; another publisher is blocked until
all installed copies of that ID are uninstalled, including disabled copies.
Third-party key/value data is isolated by public-key fingerprint and plugin ID.
The strictest known NSFW classification among installed sources applies to all copies.

Installation requires explicit permission review. Install stays on the management
page and closes the verification session; Install and browse reuses that verified
session for the works catalog. Opening still fetches and verifies a signed remote
manifest, but content-addressed modules can reuse Wasmline's hash-checked artifact
cache (128 MiB configured limit). Installation pins the verified SHA-256 of the
exact signed manifest bytes; opening rejects changed bytes even if the same
publisher signs them. Installation does not imply offline availability. Legacy
installations without that digest require one explicit review/reinstall; an
unchanged release retains its original installation timestamp. Returning or
replacing a load cancels obsolete work and closes acquired handles before another
session is accepted.

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

Users subscribe to catalog URLs rather than entering Ed25519 keys, key IDs, or
individual WLM URLs. Every installation requires a compatible release, matching
signed source provenance, a valid Ed25519 signature, and explicit permission approval.
Official releases use the public verification key supplied when building the app.
Third-party releases declare their own signing identity and exact signed-manifest
SHA-256; users explicitly approve the exact version, publisher fingerprint, and
permissions before execution. Declared keys apply only to that release, not an
application-wide trust list. Catalog inclusion is not certification, and a valid
signature does not prove safety.

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
4. Open a plugin's details, select a compatible release, and review its actual
   source links, signer, and permissions. For a third-party release, compare the
   displayed publisher fingerprint with an independent publisher channel and
   explicitly accept that exact version/fingerprint/permissions. Install keeps
   this page; Install and browse enters works after signature/digest verification.
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
do not invent hashes or source commits. Third-party publishers sign each release
with their own private key and declare `signing` (Ed25519 `keyId` and 32-byte
`publicKeyHex`) plus `manifestSha256`, the SHA-256 of the exact signed manifest
bytes. Signing may inherit from the plugin, but every release needs its own digest.
Neither a subscription nor the declaration grants trust: users approve each new
version, fingerprint, and permissions before execution. A changed key is a publisher
change, not a transparent update; same-ID installations must be uninstalled first.
Unsigned packages remain rejected. All downloads must be served without redirects.
Keep provider code, artifacts, and private keys outside evilo. See the template
README for export commands.

## Configuration

| File or build input | Owned values |
| --- | --- |
| `gradle/libs.versions.toml` | Dependency/compiler versions, SDK levels, JDK, SDK version |
| `project.properties` | App identity, release version/code, iOS deployment target |
| `gradle.properties` | Gradle/Kotlin execution configuration |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution/checksum |
| `evilo-ios/Configuration/Config.xcconfig` | Apple signing and shared property mappings |
| `plugin-repository/plugin.json` | Default bundled catalog, editable by publishers |
| `EVILO_PUBLIC_KEY_HEX` / external `public.key` | Official Ed25519 public verification key supplied to app configuration generation |

`project.properties` remains plain Java/Xcode-compatible `KEY=value` assignments.
Dependency versions are not duplicated in module scripts. `wasmline.localPath`
is an optional composite, never a committed absolute checkout path. It may
execute upstream tasks and requires that checkout's toolchain/artifacts.

## SDK, signing, and storage

The SDK coordinate is `io.github.crowforkotlin.evilo:evilo-library:1.0.0`.
Templates consume this library rather than copying models/protocols. Until
publication, use `-Pevilo.sdkPath=../evilo`. There is no third SDK repository;
changes are maintained once in `evilo-library`. Consolidation preserves the
serialized protocol and service packages; it does not change the bridge ABI.

`evilo-core` owns plugin connections and reader state without depending on Compose.
Sessions return permission-checked image bytes; `evilo-app` validates image headers
before Coil decodes them. The app supplies its build-configured official public
key to `PluginSessionFactory`. `releaseVerificationKeys()` selects either that
legacy official lookup or the reviewed release's declared publisher key; it never
adds a catalog key to a global trust list.

`EviloPluginService` uses synchronous fixed JSON envelopes; `EviloHostService`
provides capabilities and private key/value storage. Required major/minor,
capabilities, and app version bounds are checked before loading. HTTP request
construction/parsing is separate from async host transport. Continuations carry
explicit context; service signatures do not change for optional fields. Legacy
manga API 2 remains an SDK adapter, not an installable API-2-only package format.

App compilation runs `:evilo-app:generateEviloAppConfiguration` and requires an
existing official Ed25519 public key: set `EVILO_PUBLIC_KEY_HEX` to its raw 32-byte
hexadecimal value (64 hexadecimal characters), or provide an existing external
`public.key`. The environment value takes precedence. Without it, the file is
read from `~/.evilo/signing/public.key`, or from the directory selected by
`-Pevilo.signingKeyDirectory=/absolute/external/directory`. Only the normalized
public key and official key ID `evilo-root-v1` are embedded. A missing or invalid
public key fails the build; compilation never generates keys or reads the
private key. Keep the official key stable across app releases to preserve legacy
official-package verification and data identity.

`:evilo-app:generatePluginSigningKeys` remains an explicitly invoked operator task
for creating/preserving an external official pair; it is not a compilation
dependency or an automatic setup step. App builders need only the public key.
`.github/workflows/android-development.yml` is a manual-only Android development
build. Configure the repository Secret `EVILO_PUBLIC_KEY_HEX`; it builds a
debug APK artifact, not an app release. The public key remains extractable from
the APK; using a Secret avoids a source literal, not reverse engineering.
No signing private key belongs in the app build environment.

Official operators and third-party publishers retain their own private material
outside repositories, resources, source archives, and catalogs; never print,
commit, cache, or publish it. A publisher may supply their preserved seed through
a private GitHub Actions Secret for signing, never through committed source. Independent publishers do not need access to the
official private key. Legacy releases without `signing` still require an official
signature; they are not an unsigned-loading fallback.

Installation records retain the reviewed release's signing identity, permissions,
and verified manifest digest. Every third-party version requires explicit review.
A fingerprint is the lowercase SHA-256 of the raw public-key bytes, not of its
hexadecimal text or key ID; compare it through an independent publisher channel.
Initial acceptance does not authenticate a real-world identity or certify safety.

Plugin storage is persistent and scoped by verified publisher plus signed ID:
official releases preserve the legacy ID-only namespace; third-party storage uses
`publisher:<public-key fingerprint>:<plugin ID>`. Versions and subscriptions of the
same publisher/ID share data; another publisher cannot read retained data by
claiming that ID. The guest still accesses only `plugin.<plugin ID>`; the host
selects the persistent storage key. Default quotas are 1 MiB total,
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
| Explicit official signing-pair setup (operator only; never automatic) | `./gradlew :evilo-app:generatePluginSigningKeys` |
| Desktop compilation | `./gradlew :evilo-desktop:compileKotlin` |
| Desktop app | `./gradlew :evilo-desktop:run` |
| Browser Wasm distribution | `./gradlew :evilo-web:wasmJsBrowserDistribution` |
| Browser JS distribution | `./gradlew :evilo-web:jsBrowserDistribution` |
| iOS simulator framework | `./gradlew :evilo-app:linkDebugFrameworkIosSimulatorArm64` on macOS |
| Protocol/model tests | `./gradlew :evilo-library:jvmTest` |
| Catalog/storage/HTTP/runtime/controller tests | `./gradlew :evilo-core:jvmTest` |
| Image decoder, request approval, and image-scope tests | `./gradlew :evilo-app:jvmTest` |

App compilation requires `EVILO_PUBLIC_KEY_HEX` or the existing external
`public.key` described above; do not run key generation as a build prerequisite.
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
