# Module and configuration guide

Paths in this guide are relative to the repository root.

## Dependency direction

| Module | Project dependencies | Convention |
| --- | --- | --- |
| `:core:model` | None | `evilo.multiplatform` |
| `:core:source-api` | `:core:model` | `evilo.multiplatform` |
| `:core:source-runtime` | `:core:source-api` | `evilo.multiplatform` |
| `:feature:library` | `:core:model` | `evilo.compose` |
| `:app` | `:feature:library`, `:core:source-runtime` | `evilo.compose` |
| `:app:android` | `:app` | `evilo.android.application` |

Keep core modules independent of features and application entry points. Features
should depend on the contracts they use; construct runtime implementations in
the application rather than embedding native engine selection in feature UI.
The current application declares its runtime dependency but does not load sources.

## Shared targets

`build-logic/src/main/kotlin/evilo.multiplatform.gradle.kts` configures:

- Android KMP library, SDK levels, namespace, and host tests.
- JVM desktop and a shared JDK/bytecode target.
- iOS device ARM64 and simulator ARM64, even on non-Apple development hosts.
- Browser JS and Wasm JS, with the default source-set hierarchy.
- The common Kotlin test dependency.

Shared business code belongs in the owning module's `src/commonMain/kotlin`.
Use `androidMain`, `jvmMain`, `iosMain`, `webMain`, `jsMain`, or `wasmJsMain` only
for actual platform dependencies. There is no requirement to create empty
`expect`/`actual` implementations for every platform.

`core/model` and `core/source-api` add a Wasm WASI library target for Kotlin source
plugins. Keep host APIs out of their common code. Do not add Wasm WASI to Compose
features or the Wasmline host runtime.

Only `app/build.gradle.kts` configures browser executables, desktop distributions,
and the static `Evilo` framework. Android application packaging stays in
`app/android/build.gradle.kts` because AGP 9 separates applications from KMP
Android libraries.

## Adding a module

Register a requested module explicitly in `settings.gradle.kts`; directories are
not auto-included. Use type-safe `projects` accessors for project dependencies.

For a shared logic module, apply `evilo.multiplatform`. For Compose UI, apply
`evilo.compose`. Add only the dependencies the module uses. Use `api` when a
dependency's types are part of a public contract and `implementation` otherwise.

Serialized classes need `libs.plugins.kotlinSerialization`. Modules declaring
Wasmline service contracts or calling typed `link`/`bind` need
`libs.plugins.wasmline`; without the compiler plugin, typed entry points are not
ordinary executable implementations. The application also applies that plugin
to select native JVM engine variants by operating system and architecture.

Place Compose strings and other shared UI resources in the feature that owns
them. Set an explicit Compose resource package so moving a Gradle module does
not silently change imports.

## Value ownership

- Dependency and compiler versions: `gradle/libs.versions.toml`, also imported by
  `build-logic/settings.gradle.kts`.
- Application identity/version values: `project.properties`, read by the
  `buildlogic.eviloProperty()` helper and included by Xcode's `Config.xcconfig`.
- Build execution settings: `gradle.properties`.
- Wrapper distribution/checksum and daemon JVM criteria: their existing files in
  `gradle/`; do not edit generated criteria as if they were ordinary source.
- Source protocol version: `SOURCE_API_VERSION` in `core/source-api`, changed
  only with a compatibility decision about the service and serialized data.
- Source HTTP policy, trust keys, cache paths, and URLs: explicit runtime inputs,
  not committed machine-specific values or sample secrets.

Changing application identity properties does not rename Kotlin/Swift packages,
the `Evilo` Swift import, or the stable `app.js` browser bundle. A package or
framework rename requires updating those source references as a separate change.

For iOS path changes, update the Xcode build phase's repository-root directory,
`:app:embedAndSignAppleFrameworkForXcode`, the `Evilo` Swift import, and the
relative `project.properties` include together. Do not move signing values into
shared Kotlin source.

For version changes, account for the Wasmline compiler plugin's Kotlin version
and native artifacts. An optional local composite can require upstream native
build outputs; do not silently run those builds or change the upstream checkout.

## Validation boundary

Read-only source and Git inspection is allowed. Gradle invocation, compilation,
tests, formatters, and skill validators require explicit authorization. Provide
the relevant commands from the root README; do not execute them merely because
this guide mentions them.
