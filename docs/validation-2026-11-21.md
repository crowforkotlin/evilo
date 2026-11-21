# Validation record — 2026-11-21

This record distinguishes automated tests, compilation, and actual runtime interaction. No production signing authority, public release, or deployment was used.

## Automated test results

Counts below are test executions per target, not unique test methods.

| Module | JVM | Android host | JS browser | WasmJS browser |
| --- | ---: | ---: | ---: | ---: |
| evilo-app | 72 | 51 | 51 | 51 |
| evilo-core | 205 | 193 | 193 | 193 |

The final full-platform command completed with `BUILD SUCCESSFUL in 6m 6s` (273 tasks). The recorded XML results have zero failures, errors, or skipped test cases: **1,009 executions**. Android host tests are not on-device tests.

The final standard debug APK passed `apksigner verify` (v2, one signer), declares package `crow.evilo.app`, minSdk 24 / targetSdk 37, and includes INTERNET and ACCESS_LOCAL_NETWORK. Its SHA-256 is `028edfdc8b04a7ccaca5526f7d3fd5d4ce61c7500502ab465165a7a66f94e28d`.

Additional passing suites:

- CopyManga: 46 core JVM, 43 core WASI, 5 build-logic, and 5 Python release-tool tests.
- HotManga: 11 core JVM, 11 core WASI, 2 guest JVM, 5 build-logic, and 5 Python release-tool tests.
- Local-source: 4 permanent Python fixture regressions.
- SQLite browser worker: 2 Node regressions, explicitly run with `node --test evilo-web/src/test/js/sqlite-worker.test.mjs`. This command is not wired into Gradle.

The new mounted Compose scene tests exercise predictive shared-transition commit/cancel and the 400 ms minimum loading presentation. The dock tests retain real Skia path operations on JVM rather than substituting Android host graphics stubs. The new storage tests use real Room/Okio DataStore files to check lease handoff and cancellation while waiting.

The 600-entry catalogue regression projects Browse, source catalogue, installed management, and search while the backing list rejects deep hashing. This is not a 600-module export or a 600-card on-device stress test.

## Runtime evidence

### Desktop

Using isolated application storage and the final native-classified Pulley engine:

- Fresh startup without a seeded source.
- Source subscription, explicit HTTP consent, publisher fingerprint approval, and signed installation.
- Works and cover rendering, details, chapters, PNG reader pages, and title search.
- Closing and reopening a plugin session.
- Installation persistence after process restart.
- Source settings, installation-count removal confirmation, and removal of the isolated source/installations.

The restarted desktop log contained the SLF4J no-provider warning but no application exception. The desktop was stopped before rebuilding its classpath artifacts.

### Browser

Actual JS and WasmJS distributions were driven in fresh Chromium contexts. Signed installation, covers, details, chapter lists, and PNG reading were visually inspected; a driver exit code alone was not accepted as evidence.

Both JS and WasmJS additionally passed page reload persistence, reopening with network access to `manifest.wlm` blocked, and source removal confirmation/execution. No blocked manifest request occurred during the successful reopen, consistent with persistent verified-manifest reuse. Module downloads and fixture data were still available; this was not a completely offline test.

Both distributions logged a missing `/favicon.ico` (HTTP 404). Successful runtime runs had no failed plugin/data request. Some intermediate coordinate-driven JS lifecycle attempts missed a control and did not complete installation; those attempts are not counted as passes. The final paced JS run added screenshot-difference checks against inspected states and completed installation, reload, cached reopen, and removal; its screenshots were also inspected.

One WasmJS lifecycle attempt ran after the temporary fixture service had exited and failed with `ERR_CONNECTION_REFUSED`. After restoring the loopback fixture service, the lifecycle was repeated successfully. The failed attempt is not counted as a pass.

### Android API 37 device

An isolated `crow.evilo.app.validation` debug package was built using a temporary Gradle init script that adds an application ID suffix. The existing application and its data were not overwritten or cleared.

Observed on the connected device:

- Successful launch after fixing the DataStore ownership conflict.
- LAN permission prompt before application initialization and successful startup after approval.
- Signed synthetic-plugin loading, works/covers, details, three chapters, and decoded PNG reader pages.
- Repeated launch intents delivered to the existing root Activity.
- Landscape/portrait recreation and cold restart restored installation records without a duplicate-DataStore error.
- An edge-back gesture returned details to the catalogue. A synchronized recording showed the shared cover changing size into its catalogue destination.

Rotation settings were restored. The validation package was stopped after testing. Network transport for fixture requests used an explicitly owned `adb reverse` mapping, so this does not establish Wi-Fi LAN routing performance or behavior on every Android vendor/device.

## Signed guest/provider checks

A fresh external test authority signed generated guests and provider exports. The production private key was not read.

The corrected two-plugin synthetic run contains 100 works per plugin. Actual signed host execution covered 200 details, 612 chapters, all chapter content, pagination, title/author/tag search, decoded PNGs, and stable reopen. Wrong public key, plugin identity, provenance, and manifest digest were rejected. Separate checks covered 812 HTTP work/chapter destinations, 30 PNG responses, exact signatures and artifact sizes/hashes, source-archive provenance, CORS, and index reconstruction.

CopyManga and HotManga were loaded through the current `WasmlinePluginLoader.loadRemote` and beta1 Pulley with pinned test manifests and provenance. Each fixture run used eight mock exchanges and checked nested groups, batched chapters, server image order, object-valued `free_type`, HTTP 503 mirror retry, reopen, and wrong-key rejection.

Anonymous live signed operations passed catalogue, search, details, chapters, and content for both providers:

| Provider | Catalogue results | Search results | Chapters | Image references | HTTP requests | Images downloaded |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CopyManga | 50 | 4 | 43 | 81 | 5 | 0 |
| HotManga | 21 | 4 | 43 | 81 | 5 | 0 |

These observations are not a public signed release and do not validate downloading every real-provider image.

## Failures found and fixed

- JS persistence interop used optional chaining unsupported by the Kotlin embedded-JS parser.
- SQLite OO1 `getColumnNames()` throws for zero-result-column statements; PRAGMA writes and DDL now return an empty column list.
- The desktop final consumer selected an unclassified engine jar without its native library; applying the Wasmline runtime attributes on that consumer resolves the native host variant.
- The browser runtime rejects concurrent loading; browser configuration is now single-threaded while native hosts retain runtime locking.
- Provider `free_type` metadata can be an object, not just an integer; metadata parsing preserves the JSON value and has regressions for scalar/string/object forms.
- Synthetic plugin icons exceeded the host image-side bound when a cover was reused; dedicated 256-by-256 PNG icons now preserve the safety limit.
- Android replacement/repeated Activity instances could open the same DataStore before the previous environment closed. A process-level storage lease waits for complete shutdown; the root Activity also uses `singleTask` to avoid duplicate launcher roots.
- Browser test bundles required executable Skiko runtime configuration; asynchronous common transport tests required virtual-time-compatible coroutine execution.

## Remaining limits and warnings

- No iOS native compilation or simulator/device launch: Xcode is unavailable on this Linux host. XML/plist/assets and matching English/Chinese resource names were checked separately.
- No Android emulator was configured. The available API 37 physical device was used instead.
- No full 600-plugin signed export or device stress run. An extrapolation from the generated workload suggested approximately 53 GB and 2.4 hours; that estimate is not a measured 600-plugin run.
- Online AOT catalogue validation reported `WLAOT002`; local profiles and actual signed loads passed.
- Android debug lint reported five warnings, no errors: API-specific back attribute, large splash vector, obsolete `v24` resource folder, and two missing monochrome launcher-icon tags.
- The browser favicon 404 remains. Predictive cancellation has mounted scene coverage, but not a frame-by-frame physical-device cancellation recording.

## Final text-only recheck

After the request to stop loading images into the conversation, validation used XML test reports, application logs, semantic Android UI dumps, HTTP errors, and automated comparisons with previously inspected browser states. No further image was displayed or read into the conversation.

- Recounted the eight app/core XML result folders: 1,009 executions, zero failures/errors/skips.
- Reverified the standard APK signature and SHA-256 recorded above.
- Reran both provider Python release-tool suites (5 tests each), the fixture suite (4 tests), and the SQLite worker suite (2 tests); all passed.
- Reran actual JS and WasmJS installation, works, details, PNG reader, page reload, cached reopen with manifest requests blocked, source removal, and reload after removal. Eight automated image comparisons per distribution passed (mean RGB-channel difference 0.00–2.55 on a 0–255 scale; threshold 20). No plugin/data request failed. Only the existing favicon 404 was reported. These are comparisons against prior inspected states, not a new visual inspection.
- Reopened the isolated API 37 Android package. UI text/content descriptions confirmed the signed works catalogue, comic details, three chapters, and reader page nodes. A repeated launch retained the same process and delivered its intent to the existing Activity. A later cold-start foreground check was interrupted by the device switching to another application; that attempt is not counted as a successful foreground/persistence check. Earlier completed cold-start and rotation observations remain recorded above.
- All four repositories passed `git diff --check`.

Commands for the independently rerun suites:

```sh
node --test evilo-web/src/test/js/sqlite-worker.test.mjs
python -I ../evilo-local-source/scripts/test_fixtures.py
python -I ../evilo-plugin-copymanga/.github/scripts/test_merge_catalog.py
python -I ../evilo-plugin-hotmanga/.github/scripts/test_merge_catalog.py
```

The final browser driver and textual outputs are session-local under `$CLAUDE_JOB_DIR/tmp`: `browser_final_assertions.py`, `browser-js.log`, and `browser-wasm.log`. The driver uses existing local reference images; it is not a standalone repository test.

## Evidence locations

Temporary evidence is session-local and may be removed by the operating system:

- `/tmp/evilo-final-all-validation.log`: final full-platform validation command.
- `/tmp/evilo-storage-lifetime-validation-final.log`: storage lifecycle test/rebuild results.
- `/tmp/evilo-final-js-{works,comic,reader}.png`: inspected JS runtime endpoints.
- `/tmp/evilo-lifecycle-wasm-retry-*.png` and `/tmp/evilo-final-lifecycle-js-*.png`: inspected WasmJS/JS reload/cache/removal lifecycles.
- `/tmp/evilo-android-{ready,catalogue,comic,reader,rotation,permission,permission-ready}.png`: inspected device states.
- `/tmp/evilo-android-gesture.mp4` and `/tmp/evilo-android-gesture-frames.png`: synchronized device edge-back recording.
- `/tmp/evilo-source-validation.qRnPe3I2/{host-smoke-icons,http-index-icons,generate-icons}.log`: signed synthetic fixtures.

Repository outputs include `evilo-android/build/outputs/apk/debug/evilo-android-debug.apk` and both `evilo-web/build/dist/{js,wasmJs}/productionExecutable` directories. The normal APK is rebuilt without the isolation init script; the isolated validation APK is not a release artifact.
