# Module and configuration guide

Paths are relative to the evilo repository root. Follow the prefixed top-level
module naming used by Wasmline, with explicit Gradle settings includes.

## Ownership and dependencies

| Module | Direct project dependencies | Convention |
| --- | --- | --- |
| `evilo-library` | None | `evilo.multiplatform` |
| `evilo-core` | `evilo-library` | `evilo.multiplatform` |
| `evilo-app` | `evilo-core` | `evilo.compose` |
| `evilo-android` | Shared app | `evilo.android.application` |
| `evilo-desktop` | Shared app | `evilo.desktop.application` |
| `evilo-web` | Shared app | `evilo.web.application` |

`evilo-ios` is an Xcode launcher, not a Gradle project. Dependencies flow
`evilo-app -> evilo-core -> evilo-library`, never in reverse. These are the only
shared application modules; features are packages, not Gradle projects.
Platform launchers call the same `App()`; they do not copy page implementations.
`evilo-app` owns all Compose screens, MD3 theme/font/layout dimensions, generated
resources, and image decoding. Browse/More, settings/source/detail pages, confirmation dialogs, the reader,
and the existing unconnected library screen all live there. `evilo-core` owns
catalogs, HTTP, persistence, signed loading, sessions, and reader/app controllers.
Sessions return bytes so host logic never depends on Compose. The library-screen
foundation is not a working bookshelf tab.

Preserve existing Kotlin packages during consolidation: `crow.evilo.model`, `crow.evilo.plugin.api`,
`crow.evilo.plugin.runtime`, `crow.evilo.plugin.catalog`, `crow.evilo.network.ktor`,
`crow.evilo.storage`, `crow.evilo.ui`, `crow.evilo.feature.*`, and platform/app
packages. Packages need not match module names; reader/app controllers live in
`evilo-core` while Compose pages live in `evilo-app`. Keep source paths consistent
with packages, including tests. The library contains only models/protocol code,
not host HTTP, Compose, catalog state, or filesystem backends.

No real provider belongs in evilo or `../evilo-plugin-template`. The template
contains `evilo-plugin-core`, `evilo-plugin`, and an included
`evilo-plugin-build-logic` with base/plugin convention modules. Third-party repos
use `evilo-plugin-<provider>` and a matching core module. Keep host code, resources,
build configuration, and documentation provider-neutral; do not include specific
provider examples or repository links. Provider cores do not depend on
guest-generated fields; guest entrypoints pass release identity/descriptor into
portable implementation.

The template remote is the user-supplied `evilo-plugin-templatea`; its local path
remains `evilo-plugin-template`. Preserve that distinction. Source changes do not
create GitHub fork relationships, commits, or hosted releases automatically.

## Targets and conventions

`evilo-build-logic/base` owns KMP/Android conventions and property lookup;
`evilo-build-logic/app` owns Compose/desktop/web conventions and signing/catalog
tasks. Kotlin DSL accessor generation applies base conventions without consumer
catalogs, so target/dependency configuration waits for the actual `libs` catalog.
Do not duplicate dependency versions in scripts.

Shared modules retain Android KMP library/host-test, JVM/JDK, iOS device/simulator
ARM64, JS, and Wasm JS targets with the default hierarchy. Only `evilo-library` adds
Wasm WASI. Providers declare JVM/WASI portable libraries and a WASI guest binary.
Keep host HTTP, Compose, storage backends, and native runtime engines out of guests.

`evilo-app` exports the static `Evilo` Apple framework; it is not a desktop/web
executable. `evilo-desktop` owns desktop startup/distributions. `evilo-web` owns
browser viewport startup, JS/Wasm executables, and HTML/CSS loading assets.
Android packaging is in `evilo-android` because AGP separates applications from
KMP Android libraries. Apple targets stay declared on non-macOS hosts.

Shared source belongs in `commonMain`; platform code implements real dependencies
only. Koin supplies one app-owned Ktor transport, the common MVI stores, and the
Coil loader. Core stores expose `StateFlow` plus typed `dispatch(intent)`; Compose
pages do not construct repositories or clients.

For framework-specific implementation files, feature edits, and resource
lifetime, read [the architecture guide](architecture-guide.md). It covers Compose
MD3, MVI/coroutines, isolated Koin, Coil/Ktor, DataStore/Room3, KSP, and browser
storage without duplicating dependency versions.

DataStore stores typed Protobuf settings and plugin KV; Room3 stores subscriptions,
catalog caches, installations, mandatory floors, and pending KV deletions. KSP
generates Room implementations for all configured targets. Platform bindings use
Android private files, JVM app data, iOS Application Support, or browser OPFS.
Browsers use `WebOpfsStorage` and a `WebWorkerSQLiteDriver` with a bundled module
worker/SQLite runtime; one tab per origin owns the SAH pool via a Web Lock. No
old JSON/localStorage migration or memory fallback belongs in production startup.
Plugin callbacks stage synchronous KV changes under per-publisher/ID storage-key
mutexes; DataStore commits after a successful host operation. StoreLock protects staged
memory, not disk persistence. Native multi-process/device synchronization is not
provided. See `docs/application-architecture.md` for API and worker setup details.

## Build configuration

- Dependencies/compiler/SDK/JDK versions: `gradle/libs.versions.toml`.
- App identity/version/iOS deployment: Java/Xcode-compatible `project.properties`.
- Execution settings: `gradle.properties`, wrapper, and daemon files.
- Protocol major/minor/capabilities: `evilo-library`, not app release properties.
- Catalog normalization/parser/default URL: `evilo-core`; bundled `plugin.json`: `plugin-repository`.
- Official public key: build-supplied `EVILO_PUBLIC_KEY_HEX` or existing external `public.key` under `evilo.signingKeyDirectory`; compilation never creates keys.
- Publisher signing: reviewed per-release `signing` and exact `manifestSha256`, not an app-wide trust list.
- Runtime permissions: reviewed per-release origins/methods/headers/storage and HTTP consent.

The SDK publication is `io.github.crowforkotlin.evilo:evilo-library`, versioned once in the catalog.
A configured publication is not proof of availability. Templates may explicitly
substitute `-Pevilo.sdkPath=../evilo`; never copy models/service interfaces.

Register modules explicitly and use type-safe `projects.evilo*` accessors. Apply
serialization for serialized models, and the Wasmline compiler plugin where typed
services are bound/linked. Public contracts use `api`; hidden dependencies use
`implementation`. The bundled catalog is generated from one root JSON file;
font/theme resources live in `evilo-app`, not duplicate platform resource trees.
All UI resources use `crow.evilo.app.resources`. Both app configuration and the
bundled catalog generation tasks are registered in `evilo-app`.
The app injects its build-supplied official public key into `PluginSessionFactory`
in `evilo-core`. Catalog signing declarations apply only to the reviewed release
and never replace the official key or extend a global trust list.

Xcode's repository root is `$SRCROOT/..`; its configuration includes
`../../project.properties`. Framework build phase, Gradle task, Swift import,
shared properties, and path changes must be updated together. Package/app ID or
framework renames are separate from directory changes. Preserve installed app
identity and data during a requested update; do not uninstall or clear data to
solve a signing conflict without explicit approval.

## Protocol and trust

Use fixed synchronous JSON-envelope WasmlineService signatures. No suspending
methods, overloads, properties, generics, default args, varargs, or service
parameters without checking read-only upstream support. Preserve plugin-qualified
IDs and explicit continuations. Legacy manga API 2 is an adapter, not the generic
app's installable protocol. Optional fields do not change bridge signatures.

Preserve the official Ed25519 verification key across app releases. App compilation
requires existing public material and never creates keys or reads private material;
`generatePluginSigningKeys` is a separately invoked official-operator task.
Third-party publishers keep their own private keys and declare per-release signing
and manifest digests. Users approve the exact version, fingerprint, and permissions;
unsigned packages remain rejected. Private keys stay outside repos, resources,
source ZIPs, and catalogs. Same-ID/different-publisher installations are blocked;
third-party KV is isolated by fingerprint and plugin ID. Signing does not prove
network or execution safety.

Use signed remote manifests and explicit package/network policies. Do not accept
raw modules through UI, copy sample trust keys, grant wildcard origins, or claim
browser CORS is bypassed. The upstream Wasmline checkout stays read-only; composite
builds are opt-in and may need its artifacts/toolchain.

## Execution boundary

Read source and Git state before changes. Preserve unrelated edits. No branches,
commits, or pushes unless requested. Tests, builds, packaging, formatters,
validators, servers, device commands, installation, and screenshots need a specific
request. APK build/install authorization covers that APK and installation only,
not desktop/web/iOS builds, unit tests, app launch, or screenshots. Select manual
commands from README and report observed results separately from source changes.
