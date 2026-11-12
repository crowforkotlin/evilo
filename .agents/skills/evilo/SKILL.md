---
name: evilo
description: Maintain evilo's Kotlin Multiplatform/Compose application architecture and Wasmline plugin SDK/runtime. Use for shared features, framework integration, dependency wiring, catalogs, persistence, Gradle, and platform launchers. This skill does not authorize upstream Wasmline edits or automatic validation.
---

# evilo development

Use this skill and its referenced guides as the source of project-specific
instructions. Keep project rules in `.agents/skills/`; do not create a separate
root instruction file.

## Select the owning module

Read the relevant implementation before editing.
Use [the module guide](references/module-guide.md) when adding modules, changing
source sets, moving application entry points, or editing Gradle configuration.

- `evilo-library` owns portable media models, the synchronous plugin/host protocol,
  and the legacy manga adapter. It is the single published plugin SDK.
- `evilo-core` owns network policy, storage, catalogs/install/update state, Wasmline
  loading, plugin sessions, and reader/application controllers. It does not depend
  on Compose; sessions expose image bytes, not `ImageBitmap`.
- `evilo-app` owns all Compose pages, MD3 theme/font/dimensions, image decoding,
  resources, app composition, and platform persistence bindings. Do not split
  features into Gradle modules or copy pages into platform launchers.
- `evilo-android`, `evilo-desktop`,
  `evilo-web`, and the Xcode-only `evilo-ios` own launchers, not business modules.
- `evilo-build-logic/base` and `evilo-build-logic/app` own conventions and build tasks.

Keep provider implementations outside evilo and the provider-neutral template.
Third-party repositories use `evilo-plugin-<provider>` with guest/core modules.
The SDK is maintained once in evilo, not copied into each provider.

The common app code supplies Browse/More, JSON sources, and image/text viewing;
library/history/downloads remain unimplemented. Distinguish code/configuration
from observed builds, installs, signed loading, and runtime behavior.

## Use the existing application frameworks

Read [the architecture and framework guide](references/architecture-guide.md)
when changing features, dependency injection, state, HTTP/images, storage, or
resource lifetime. It names the implementing files and existing call sequences.

Compose observes typed MVI stores in `evilo-core`; Koin owns dependencies in one
isolated application context. Reuse the app-owned Ktor transport and permission-aware
Coil loader. Keep settings/plugin KV in typed Protobuf DataStore and catalog records
in Room3; KSP generates Room implementations. Framework versions belong only in
`gradle/libs.versions.toml`. Read `docs/design-system.md` before changing shared UI
tokens or navigation; do not replace the established MD3 theme or font.

## Locate code

If a repository has a `.codegraph/` directory at its root, use `codegraph_explore`
before grep/find or reading files to understand or locate code. If the MCP tool
is unavailable, use `codegraph explore "<symbol names or question>"` from that
repository. Name files or symbols to request their current source and call paths.
If there is no index, use ordinary source inspection; do not create an index
unless the user requests one.

## Keep the plugin contract portable

`EviloPluginService`, `EviloHostService`, and legacy `MangaSourceService` extend
Wasmline's synchronous `WasmlineService`.
Do not add service properties, suspending methods, overloads, generic methods,
default parameters, or `vararg` without checking upstream support first.

Keep plugin request construction and response parsing separate from asynchronous
host network I/O. `MangaSource` may suspend; `MangaSourceService` must not. Pass
request context explicitly so concurrent host requests do not depend on a
plugin's mutable last-request state.

Preserve plugin-qualified `MediaId` and legacy source-qualified `MangaId` values.
Review protocol major/minor and capabilities for serialized/service changes;
legacy `SOURCE_API_VERSION` is separate. Protocol constants are not application
release versions and do not belong in `project.properties`.

Use Wasmline's signed-manifest loader rather than accepting arbitrary raw module
paths from application UI. Preserve error details and close acquired handles on
negotiation failure. Normal source disposal closes that source, not the global
Wasmline runtime. Do not swallow coroutine cancellation.

HTTP clients, trusted keys, caches, repository URLs, and any proxy are supplied
explicitly. Do not copy upstream sample trust keys. When implementing HTTP,
enforce host-approved URL/redirect and credential policies and response limits;
a plugin signature is not network authorization. Do not claim browser CORS can
be bypassed with Wasm.

## Maintain catalog JSON

Read [the catalog contract](../../../docs/plugin-catalog.md) for metadata and
release changes. Publishers may host their own catalog without a PR to evilo;
`plugin-repository/plugin.json` is only the bundled source. Filenames and `.json`
suffixes are not required. The public format needs no separate JSON Schema.

Keep common kinds, languages, source URL, permissions, and protocol declarations
once per plugin. Releases inherit omitted fields; explicit overrides replace the
whole field, never merge it. String authors, optional icons, ordinary dates, and
derived version/date summaries are supported by `CatalogJson.kt` before typed
decoding in `CatalogParser`. Preserve full legacy input and explicit metadata;
defaults must be deterministic. Never invent release revisions, source archives,
or hashes to reduce authoring work; use actual release export results.

Catalogs carry no review/certification state. Browse shows installed plugins only;
All/Installed filters belong to source catalogs. Info opens plugin details without
connecting a works session. Duplicate merging is display-only, defaults on, and
keeps source-qualified installation keys and explicit/oldest default source choices.
Do not silently switch sources or compare mirror versions to choose a default.
Independent sources cannot add trusted signing keys; install/update still requires
the app authority's signed releases and explicit permission approval. Changing
common defaults must not silently alter approved installation permissions.
The built-in subscription can be disabled, not removed. Every mandatory version
floor is scoped to its subscription, including the built-in one. Preserve the
existing Room record migration when renaming bundled source IDs or URLs; do not
discard caches, installation timestamps, disabled state, or plugin KV.

## Configuration and platform work

Read [the configuration section](../../../README.md#configuration) for value
ownership. Reuse the version catalog in `evilo-build-logic`; do not duplicate dependency
versions in module scripts. Keep `project.properties` compatible with both Java
properties and Xcode configuration syntax.

Keep Android, JVM, both iOS ARM64 targets, JS, and Wasm JS in shared-module target
declarations. Only plugin-contract/model modules also need Wasm WASI. Do not
remove Apple targets merely because the current machine is not macOS.

If upstream Wasmline API details are needed, inspect the explicitly supplied
checkout or published source. Treat it as read-only unless upstream edits are
requested. Use `wasmline.localPath` for an opt-in composite, not an absolute
build path.

## Execution and reporting

Use direct engineering communication: name the files, symbols, commands, and
observed changes. Keep comments and repository documentation in English; answer
in the user's language. Avoid undefined labels and unsupported success claims.

Preserve unrelated working-tree changes. Do not create branches, commit, or push
unless the user explicitly requests those operations.

Implementation requests do not authorize validation. Do not run Gradle, tests,
builds, packaging, linters, formatters, validators, application servers, or device
commands without a specific user request. This includes Gradle configuration
tasks, skill validators, `git diff --check`, and validation hidden in scripts.
Reading files and Git status/diffs remains allowed.

Report implementation separately from validation. State "not run" when relevant,
and select manual commands from [the README](../../../README.md#manual-commands)
with their platform prerequisites. Never claim a configured target was built or
run based only on source inspection.
