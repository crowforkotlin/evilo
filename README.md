# evilo

evilo is a Kotlin Multiplatform manga-reader project targeting Android, iOS,
desktop JVM, and browsers. Manga sources use Wasmline plugins rather than
Android APK extensions.

## Current scope

This repository contains the application and plugin foundations, not a complete
reader. `App()` passes an empty `LibraryUiState` to the shared `LibraryScreen`.
The source modules define manga models, a versioned Wasmline service contract,
and a loader that adapts the service to asynchronous host calls.

Source installation, a concrete HTTP client, trust-key distribution, persistence,
image rendering, reader navigation, and downloads are not implemented. No source
plugin, source repository URL, signing key, or proxy service is bundled. The
application does not invoke the source loader yet.

## Modules

```text
app/
  src/commonMain/       Application composition and shared resources
  src/jvmMain/          Desktop entry point
  src/webMain/          JS/Wasm browser entry point, HTML, and font
  src/iosMain/          Compose UIViewController entry point
  android/             Android application module and resources
  ios/                 Xcode project and SwiftUI launcher
core/
  model/               Serializable manga, chapter, and page models
  source-api/          Host source interface and Wasmline service contract
  source-runtime/      Wasmline loading, service calls, and handle ownership
feature/
  library/             Shared library UI and state
build-logic/           Included Gradle convention-plugin build
.agents/skills/evilo/   Project-local Agent Skill
```

| Gradle module | Responsibility | Configured targets |
| --- | --- | --- |
| `:app` | Compose application, desktop/browser executables, `Evilo` iOS framework | Android library, JVM, iOS ARM64, iOS simulator ARM64, JS, Wasm JS |
| `:app:android` | Android manifest, activity, signing, and packaging | Android application |
| `:core:model` | Serializable domain models | All shared application targets plus Wasm WASI |
| `:core:source-api` | Plugin ABI and asynchronous host interfaces | All shared application targets plus Wasm WASI |
| `:core:source-runtime` | Wasmline host implementation | All shared application targets |
| `:feature:library` | Library screen and state | All shared application targets |

`app/ios` is an Xcode project, not a Gradle subproject. AGP 9 requires the Android
application to remain separate from the KMP Android library. These two launchers
contain platform startup and packaging, not shared business logic.

```text
:app:android --> :app --> :feature:library --> :core:model
                  |
                  +----> :core:source-runtime --> :core:source-api --> :core:model

app/ios --> Evilo framework produced by :app
```

Every shared module owns its own `commonMain`, `commonTest`, and any required
platform source sets. Do not create empty platform implementations for code that
can stay in `commonMain`. New features belong in their own KMP modules rather
than growing another catch-all `shared` module.

`evilo.multiplatform` declares shared targets and compiler settings.
`evilo.compose` adds Compose and resource support. Only `:app` declares browser
executables and an Apple framework; ordinary libraries do not produce apps.
The model and source API also declare `wasmWasi` so Kotlin source plugins can
consume the same contract without depending on Compose or the host loader.

## Configuration

| File | Owned values |
| --- | --- |
| `gradle/libs.versions.toml` | Kotlin, AGP, Compose, Wasmline, JDK target, SDK levels, and library/plugin coordinates |
| `project.properties` | Application identity, release version/code, and iOS deployment target |
| `gradle.properties` | Gradle/Kotlin execution settings and the settings-plugin version |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution and checksum |
| `gradle/gradle-daemon-jvm.properties` | Generated Gradle daemon JVM criteria |
| `app/ios/Configuration/Config.xcconfig` | Apple signing team and mappings from shared application properties |

`project.properties` uses plain `KEY=value` assignments accepted by both Java
properties and Xcode configuration files. Gradle reads it through
`eviloProperty()`; Xcode includes it directly. Keep release versions in
three-component form, such as `1.0.0`, for desktop packaging. Localized screen
text remains in Compose resources; the browser document title is in `index.html`.

The Kotlin baseline follows the local Wasmline reference's compiler-plugin
version. Change Kotlin, its serialization/Compose plugins, and the Wasmline
compiler plugin as a compatible set. Declaring dependencies does not establish
that a particular publication or native artifact is available; resolution and
compilation still need to be run explicitly.

Published Wasmline coordinates are the default. To use a local checkout, provide
`wasmline.localPath` as a Gradle property; no developer-specific path is committed.
For example, a manually requested compile can use:

```sh
./gradlew -Pwasmline.localPath=/path/to/wasmline :core:source-runtime:compileKotlinJvm
```

The optional composite supplies both Gradle plugins and library substitutions.
It may execute tasks in the referenced checkout and requires that checkout's
toolchain and native artifacts when building the application.

## Wasmline sources

`MangaSourceService` is the plugin-facing binary interface (ABI).
`MangaSource` is the asynchronous application-facing interface.

```text
MangaSource.search(query, page)
  -> MangaSourceService.searchRequest(query, page)
  -> SourceHttpClient.execute(metadata, request)
  -> MangaSourceService.parseSearch(query, page, response)
  -> MangaSearchResult
```

Details, chapters, and pages follow the same request/response pattern. Wasmline
service methods are synchronous; they must not declare `suspend`, overloads,
properties, generic methods, default arguments, or `vararg`. Network I/O belongs
to the suspending host HTTP client, not a synchronous browser import. Service
methods must use their explicit request context rather than mutable per-request
global state because HTTP operations can overlap.

`WasmlineSourceLoader` accepts signed local/remote manifest sources, delegates
package verification to Wasmline, links `MangaSourceService`, and rejects an
unsupported `SOURCE_API_VERSION`. It closes the loaded handle if linking or
metadata negotiation fails. `WasmlineMangaSource` serializes service calls and
close operations; calls after close fail, including parsing an HTTP response
that arrives after close. The caller still owns cancellation of in-flight HTTP.

`MangaId` combines source ID and source-local ID. Search page numbers start at
one; image page indexes start at zero. Breaking model or service changes require
an explicit API-version decision. The initial protocol supports one HTTP exchange
per operation; multi-step authentication or scraping needs a separate design.

The application selects the Pulley native engine for Android, desktop, and iOS.
Browsers use Wasmline's browser implementation and do not depend on native engine
artifacts. Plugin packages must provide artifacts suitable for each host.

Before connecting the loader to UI, supply trusted keys, loader networking/cache
options, and a `SourceHttpClient` with explicit domain, redirect, timeout, response
size, and credential rules. Plugin-requested URLs and headers are untrusted.
Wasm isolation does not grant HTTP permission. Browser CORS rules still apply;
Wasmline cannot make arbitrary manga sites browser-accessible. Any proxy must be
an explicit, configured product decision, not a built-in endpoint.

## Manual commands

Commands below are instructions, not recorded results. Agents must not run tests,
Gradle tasks, builds, formatters, servers, or validators without an explicit user
request for that operation.

Use the JDK specified by the version catalog and Gradle daemon criteria. Android
requires the configured SDK and a local SDK installation. iOS requires macOS and
Xcode; set `TEAM_ID` in the Xcode configuration for device signing. Browser tests
require a browser supported by the Kotlin test runner.

| Operation | Command from the repository root |
| --- | --- |
| Domain tests on JVM | `./gradlew :core:model:jvmTest` |
| Domain tests on Android host | `./gradlew :core:model:testAndroidHostTest` |
| Domain tests on Web | `./gradlew :core:model:jsBrowserTest :core:model:wasmJsBrowserTest` |
| Domain tests on iOS simulator | `./gradlew :core:model:iosSimulatorArm64Test` |
| Desktop compilation | `./gradlew :app:compileKotlinJvm` |
| Desktop application | `./gradlew :app:run` |
| Android debug APK | `./gradlew :app:android:assembleDebug` |
| Browser Wasm distribution | `./gradlew :app:wasmJsBrowserDistribution` |
| Browser JS distribution | `./gradlew :app:jsBrowserDistribution` |
| Browser development server | `./gradlew :app:wasmJsBrowserDevelopmentRun` or `./gradlew :app:jsBrowserDevelopmentRun` |
| iOS simulator framework | `./gradlew :app:linkDebugFrameworkIosSimulatorArm64` on macOS |

For the iOS application, open `app/ios/iosApp.xcodeproj`. Its build phase invokes
`:app:embedAndSignAppleFrameworkForXcode` with Xcode's platform and signing settings.

## Project skill

Project-specific agent instructions are maintained only in `.agents/skills/`.
Use [`.agents/skills/evilo/SKILL.md`](.agents/skills/evilo/SKILL.md) and its referenced
guides. The skill contains the required `name` and `description` frontmatter from the
[Agent Skills specification](https://agentskills.io/specification).

If `skills-ref` is installed, the skill can be validated manually with
`skills-ref validate .agents/skills/evilo`. This is separate from application
builds and tests and also requires explicit authorization before an agent runs it.
