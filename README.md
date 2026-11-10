# evilo

evilo is a Kotlin Multiplatform media-reader application with one Compose
Material Design 3 UI for Android, desktop, browsers, and iOS. Providers are signed
Wasmline plugins loaded from JSON catalogs, not app dependencies or APK extensions.

## Repositories

| Repository | Responsibility |
| --- | --- |
| `https://github.com/crowforkotlin/evilo` | Host app, maintained plugin SDK, official JSON catalog |
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
plugin-repository/           Bundled official JSON and catalog schema
docs/                        Plugin architecture and catalog contract
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
`MainViewController`. `evilo-app` owns colors, typography, Noto Sans SC, rounded
shapes, and width limits. Compact windows use Browse/More with a centered content
selector; at 840 dp the same navigation moves to a rail. Page content is capped
at 1320 dp and reading at 900 dp. Cards, filter chips, source/install counts, empty
states, settings, and safety sections adapt without platform-specific page copies.

Shared modules retain Android, JVM, iOS ARM64/simulator ARM64, JS, and Wasm JS.
Only `evilo-library` also declares Wasm WASI for guests.
`evilo-desktop` and `evilo-web` own executables; `evilo-app` exports the static
`Evilo` Apple framework. Apple builds require macOS/Xcode, not target removal.

## Current behavior and limits

Browse lists JSON sources and supports search plus All/Installed/Officially
reviewed filters. The manga/novel/anime/film selector filters both available and
installed plugins. More contains source management, NSFW preference, installed
plugins, and safety information. NSFW is hidden by default.

Users do not enter Ed25519 keys, key IDs, or individual WLM URLs. Installations
require a compatible release, matching signed source provenance, the app authority
signature, and explicit permission approval. Official review does not prove safety.
Other authors may maintain JSON catalogs, but cannot supply trusted keys or grant
themselves official certification.

Image/text reading code is shared. Video URLs open externally after approval;
custom playback headers are unsupported. Library/history, saved reading progress,
downloads, cloud sync, login, and in-app video are not implemented.

The bundled `plugin-repository/official.json` remains empty until actual reviewed
signed packages and matching source archives are published. The default refresh
URL is `https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/official.json`.
Pushing application or plugin source code does not publish an installable plugin
package. SDK publication is configured, not established remotely.

## Install a plugin

The app installs releases from a published JSON catalog. A source repository
homepage, individual `.wlm` or `.wasm` file, and signing key are not subscription
addresses.

1. Open Browse and tap the App Bar's `+` action to open Plugin Sources.
2. Enter the publisher's direct JSON catalog URL and tap Subscribe. Prefer HTTPS;
   plain HTTP requires explicit cleartext consent.
3. Select a content type supported by the plugin. NSFW entries remain hidden
   unless their preference is enabled in More.
4. Open a plugin entry, select a compatible available version, and tap Install.
   Review its source and requested permissions, then tap Approve and Install.
5. Use Open to enter an installed plugin. No WLM URL, key ID, or Ed25519 key is
   entered by the user.

The official source is already subscribed. An empty `official.json` means there
are no official releases to install; refreshing an empty list does not create a
plugin. The illustrative URLs in the catalog documentation are not installable
releases.

A publisher must first export a package signed by the authority embedded in the
app, its actual source archive, icon, and `plugins.json`. Host `manifest.wlm`, its
content-addressed `artifacts/` directory, the source archive, icon, and catalog
at directly accessible HTTP(S) URLs. The app rejects download redirects. Keep
these files outside the host repository; only reviewed catalog metadata belongs
in its official list. See the independent template's README for release export,
and `docs/plugin-catalog.md` for the catalog contract. Without a published catalog
and matching signed package, there is no usable installation address to enter.

## Configuration

| File | Owned values |
| --- | --- |
| `gradle/libs.versions.toml` | Dependency/compiler versions, SDK levels, JDK, SDK version |
| `project.properties` | App identity, release version/code, iOS deployment target |
| `gradle.properties` | Gradle/Kotlin execution configuration |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution/checksum |
| `evilo-ios/Configuration/Config.xcconfig` | Apple signing and shared property mappings |
| `plugin-repository/official.json` | Canonical bundled official catalog |

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
Sessions return permission-checked image bytes; `evilo-app` performs bounded
bitmap decoding. The app supplies its generated public signing authority to
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
storage. Android/JVM/iOS use private files; browser localStorage has additional
quota/availability limits. No cross-device/process synchronization is implemented.

See `docs/plugin-architecture.md` for network policies and remaining runtime
limits, and `docs/plugin-catalog.md` for metadata, actual-source requirements,
compatible versions, and mandatory updates. The host checks signed revision/hash
consistency, not archive contents or reproducible builds. Current upstream does
not expose guest execution-fuel or linear-memory limits.

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
| Image decoder tests | `./gradlew :evilo-app:jvmTest` |

Android needs the configured SDK and JDK. iOS uses
`evilo-ios/iosApp.xcodeproj`; its build phase invokes
`:evilo-app:embedAndSignAppleFrameworkForXcode`. Browsers obey CORS/mixed-content
restrictions; no server or proxy is started automatically.

The old `:app:android` task and preview signing properties are obsolete. Use the
independent plugin repository's README for signing/export. Do not publish stale
binaries, assign invented source commits/URLs, or claim official review before
the actual release and source have been reviewed/published.

## Project instructions

Project rules remain in `.agents/skills/evilo/` and its module guide. Comments and
repository docs are English; user replies use the user's language. Report source
changes separately from observed builds, installs, runtime behavior, and tests.
