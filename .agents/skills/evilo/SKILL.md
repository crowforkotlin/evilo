---
name: evilo
description: Maintain the evilo Kotlin Multiplatform manga reader and its Wasmline source-plugin contracts. Use when changing evilo modules, Compose screens, Gradle configuration, source loading, or Android, desktop, browser, and iOS entry points. This skill does not authorize changes to the upstream Wasmline repository or automatic validation.
---

# evilo development

Use this skill and its referenced guides as the source of project-specific
instructions. Keep project rules in `.agents/skills/`; do not create a separate
root instruction file.

## Select the owning module

Read the relevant implementation before editing.
Use [the module guide](references/module-guide.md) when adding modules, changing
source sets, moving application entry points, or editing Gradle configuration.

- `core/model` owns serializable manga, chapter, and image-page data.
- `core/source-api` owns `MangaSource`, `MangaSourceService`, and transport data.
- `core/source-runtime` owns Wasmline loading and loaded-service lifetimes.
- `feature/library` owns library UI and state. Add other features as KMP modules
  when their implementation is requested; do not create empty feature modules.
- `app/src` owns application composition and desktop, browser, and iOS entry
  points. `app/android` and `app/ios` are platform launchers, not business modules.

The current application is a foundation: `App()` supplies an empty library state.
Do not describe source installation, persistence, downloads, or image reading as
implemented unless the relevant code exists and the claimed result was observed.

## Locate code

If a repository has a `.codegraph/` directory at its root, use `codegraph_explore`
before grep/find or reading files to understand or locate code. If the MCP tool
is unavailable, use `codegraph explore "<symbol names or question>"` from that
repository. Name files or symbols to request their current source and call paths.
If there is no index, use ordinary source inspection; do not create an index
unless the user requests one.

## Keep the plugin contract portable

`MangaSourceService` extends Wasmline's synchronous `WasmlineService`.
Do not add service properties, suspending methods, overloads, generic methods,
default parameters, or `vararg` without checking upstream support first.

Keep plugin request construction and response parsing separate from asynchronous
host network I/O. `MangaSource` may suspend; `MangaSourceService` must not. Pass
request context explicitly so concurrent host requests do not depend on a
plugin's mutable last-request state.

Preserve source-qualified `MangaId` values. Review `SOURCE_API_VERSION` whenever
changing serialized models or service signatures; protocol constants are not
application release versions and do not belong in `project.properties`.

Use Wasmline's signed-manifest loader rather than accepting arbitrary raw module
paths from application UI. Preserve error details and close acquired handles on
negotiation failure. Normal source disposal closes that source, not the global
Wasmline runtime. Do not swallow coroutine cancellation.

HTTP clients, trusted keys, caches, repository URLs, and any proxy are supplied
explicitly. Do not copy upstream sample trust keys. When implementing HTTP,
enforce host-approved URL/redirect and credential policies and response limits;
a plugin signature is not network authorization. Do not claim browser CORS can
be bypassed with Wasm.

## Configuration and platform work

Read [the configuration section](../../../README.md#configuration) for value
ownership. Reuse the version catalog in `build-logic`; do not duplicate dependency
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
