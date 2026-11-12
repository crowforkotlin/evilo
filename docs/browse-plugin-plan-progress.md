# Browse, plugin sources, and image-state progress

Date: 2026-11-11

This ledger follows the external `PLAN.md` dated 2026-11-11. It records the
current working-tree implementation, not a released application or a passed test
report. Existing catalog-format, configuration, documentation, and storage edits
were retained. The bundled `plugin-repository/plugin.json` still has no plugins.

## Evidence status

- Implementation and test files were read and edited. No Gradle task, unit test,
  compiler, resource generator, formatter, validator, server, device action,
  screenshot, commit, or publication was executed for this work.
- The configured Coil 3.6.3 JVM source artifact was inspected for decoder,
  interceptor, request, memory-cache, and platform-context APIs. This is source
  inspection, not dependency resolution, compilation, or a Coil execution result.
- All tests listed here are **written, not run**. Controller tests use explicitly
  non-production connectors/sessions. Catalog/DataStore fixtures are in memory;
  they do not prove Room generation, filesystem migration, or platform storage.
- `EviloImagesTest` invokes the real approval interceptor with controlled Coil
  chains/cache results and exercises fetcher/decoder delegation. It does not
  render Compose, execute a real Coil bitmap pipeline, or load a signed plugin.
- The original image failure has not been reproduced. Platform, a redacted error,
  and an example failing request/response are still missing; its root cause is
  unconfirmed. Cover-header support remains conditional, not silently assumed fixed.

## Stages

| Stage | Present in source | Still required |
| --- | --- | --- |
| P0: image evidence and error definition | Typed network/image failures, limits, redaction, and diagnostic UI | A real failure sample and platform evidence; no root-cause claim |
| P1: state and routes | Typed home pages/overlays, installed projection, preferences, stable source selection, return handling | Authorized compilation/tests and navigation observation |
| P2: source installation path | Sources/add/settings/catalog/detail pages, single-source checks, permission review, install-only/and-browse targets | Real signed-source installation and failure-path observation |
| P3: Browse and More | Installed-only source groups, compact Info rows, display merging, More/Settings categories | Phone/wide/dark/large-font and accessibility observation |
| P4: image UI | Missing/loading/failure states, per-image retries, collapsed safe diagnostics, session cancellation | Live HTTP/format/permission failures and Coil/Compose observation; SDK extension only if P0 warrants it |
| P5: resources, docs, validation | English/Chinese resources, architecture/design/catalog guides, test sources, this ledger | All test results, platform builds, and visual/runtime acceptance remain unobserved |

## Requirement ownership

Symbols below identify actual source files in the file map. Status for every row
is source implementation present, validation not run unless explicitly stated.

| PLAN requirement | Implementing symbols/pages | Written evidence / remaining observation |
| --- | --- | --- |
| B-01: installed-only Browse and local filtering | `projectBrowse`, `PluginHomeStore.update`, `BrowseSearchLabels` | `BrowseProjectionTest.addingCatalogEntriesDoesNotCreateInstalledBrowseRows`, `installedReleaseKindsDoNotInheritNewCatalogCapabilities`, `localSearchMatchesLocalizedTagsAndReturnsActualFilteredGroupCounts` |
| B-02: compact name/icon/tags, independent About | `PluginRow`, `PluginAvatar`, `ManagePlugin`, `OpenPlugin` | `PluginHomeStoreTest.aboutDoesNotConnectAndTheMainActionOpensTheInstalledPluginDirectly`; actual pointer/keyboard/reader semantics unobserved |
| B-03: source groups, stable target, actual counts | `BrowseSourceGroup`, `preferredInstallationKey`, `PluginBrowseScreen`, `ContentTypeSheet` | `BrowseProjectionTest.searchDoesNotChooseAnotherSourceAndExplicitSourceFilteringDoesNotChangeThePreference`; per-source raw counts stay independent of merging |
| Section 5.4: accurate empty states | `BrowseEmptyReason`, `BrowseEmptyState`, `InstalledPluginsScreen` | Projection tests cover missing installations, disabled sources, unsupported kind, NSFW and search misses; source-added/empty catalog copy is present but not rendered |
| L-01: install-only / install-and-browse | `AppPluginController.install`, `acquire`, `PluginHomeStore.approve` | Controller install-target tests; `PluginHomeStoreTest.installOnlyKeepsTheDetailAndClosesItsVerificationSession`, `installAndBrowseRetainsOneSessionAndBackReturnsToTheReviewedDetail` |
| Section 6.2: honest signed loading and cancel/replace | `PluginLoadPhase`, `PluginSessionConnector`, `PluginSessionFactory`, `LatestOperation` | `AppPluginControllerTest` covers phases, cancellation, descriptor mismatch, cleanup failure, reopening, preference commit, and replacement; no signed package was loaded |
| Section 6.3: check updates without install or switch | `PluginCatalogRepository.refresh(sourceId)`, `SourceCheckState`, `SourceCheckStatus` | Repository independent-success/failure/cache tests; `PluginHomeStoreTest.localSearchFiltersAndNavigationRemainAvailableDuringACatalogRefresh` |
| S-01: source-management entry and independent actions | `HomeAppBar`, `PluginSourcesScreen`, `AddPluginSourceScreen` | Add/download cancellation store test; pointer separation of card/switch/settings and saved form state unobserved |
| S-02: source settings and explicit destructive effects | `PluginSourceSettingsScreen`, `SourceRemovalDialog`, repository source mutations | Repository disable/remove/KV/default-pinning tests; built-in source cannot be removed and the subscription limit remains 16 |
| S-03: per-source All/Installed catalogs | `projectSourceCatalog`, `SourceCatalogScreen` | Projection tests cover installed-release kinds and uninstalled catalog entries; disabled sources remain viewable but blocked for use/install/update |
| D-01: full detail and contextual actions | `PluginDetailScreen`, `PluginReleaseChoice`, `releaseActionLabel` | Store detail/review/return tests; version list, installed/requested permissions, errors and footer layout still need rendering observation |
| D-02: accurate sources, permissions and per-copy uninstall | `PluginSourceCopy`, `PluginSourceLinks`, `PluginPermissionDialog`, `PluginUninstallDialog` | Repository stale-release/last-uninstall tests; `PluginHomeStoreTest.approvalRejectsTheExactReviewedReleaseAfterItsPermissionsChange` |
| R-01: default-on display merging | `EviloPreferences` field 5, `setMergeDuplicatePlugins`, `projectBrowse` | Preferences round-trip/failure tests and projection merge-on/off identity tests |
| R-02: installed signed ID, not name/catalog claim | `BrowsePluginItem.pluginId`, `projectBrowse` | `BrowseProjectionTest.mergingUsesInstalledIdsNotNamesOrUnverifiedCatalogClaims`; all actions still carry the raw installation key |
| R-03: explicit/oldest source, conflicts and strict NSFW | `preferredInstallationKey`, `installationOrderTime`, `hasReleaseConflict`, `isPluginNsfw`, `pinDefaultSources` | Projection chronological/selection/conflict/NSFW tests and repository tombstone/floor/release-replacement tests; source-filter-origin open does not change the global default |
| R-04: merging off preserves real records/counts | `projectInstalledManagement`, source installation counts | `BrowseProjectionTest.managementRetainsDisabledOrOrphanInstallationsAndAllKindsWithoutDisplayMerging`; preferences alter display, not Room records |
| Sections 4/10: return stack, More and Settings | `HomePage`, `HomeOverlay`, `PluginHomeStore.back/push`, `SettingsScreen`, saveable state holders | Home store and reader return/cancellation tests; system Back/Escape, scroll restoration and focus still need UI observation |
| Section 3: image states, retry and diagnostics | `ImageFailure`, `EviloAsyncImage`, `EviloImageErrorDetails`, `SessionImages`, `PluginImageDecoderFactory` | `ImageFailureTest`, image decoder tests, typed transport/policy tests and `EviloImagesTest`; real failure unconfirmed |
| Section 11: existing MD3 visual rules | `EviloTheme`, `EviloLayout`, `PluginRow`, source/settings/detail pages | Teal, ChillRoundM, shape/spacing/breakpoint tokens retained; all visual/accessibility acceptance unobserved |
| Section 12.3: persistence and safety compatibility | `EviloPreferencesCodec`, repository commit/cleanup/default methods, approved image models | Legacy four-field Protobuf and repository migration/cancellation/floor/KV tests; no Room schema change or SDK bridge change |

## Acceptance scenarios

These are traceable checks to perform, not success results.

| Scenario from PLAN section 14 | Source/test coverage | Runtime status |
| --- | --- | --- |
| First start with an empty bundled source | `plugin-repository/plugin.json`, Browse empty state, projection no-install test | Not observed |
| Add a source without installing | Catalog subscription stores metadata only; projection ignores uninstalled entries | Not observed |
| Install a supported release, then enter works directly | Home/controller install tests; reader `Search("")` on route entry | Not observed |
| About opens detail without a session | Home store About-vs-open test | Not observed |
| Failed source check retains cache without claiming success | Repository independent failure/cache/check-time tests | Not observed |
| Withdrawal, metadata change or mandatory update blocks old opening | Repository release blocks/floor tests; store keeps raw installation entries | Not observed |
| Disable/re-enable retains record/time and does not switch source | Repository duplicate-default pinning and projection source-choice tests | Not observed |
| Remove source records and close active session, retaining KV | `closeSourceSession`, source-removal confirmation, repository removal/KV test | Not observed |
| Same installed ID from two sources merges; same name/different ID does not | Projection identity/merge tests | Not observed |
| Uninstalled same-ID catalog does not replace an installed copy | Projection installed-ID and conservative-classification tests | Not observed |
| Merging off changes only visible rows | Projection raw-record/count and preference tests | Not observed |
| Unavailable default requires an explicit choice | Projection disabled/removed/unsupported choice tests; repository preference tombstones | Not observed |
| Same-ID/same-code declaration conflict is visible and records stay separate | Cross-source conflict test; installed/requested permissions rendered separately | Not observed |
| One-copy uninstall retains other copy/KV; last uninstall retries KV cleanup | Repository uninstall/failed deletion tests | Not observed |
| Install-only closes verification; install-and-browse reuses it | Controller and home store target tests | Not observed |
| Back/replacement cannot publish an obsolete session, error or image | Controller/store/LatestOperation/reader/image-scope cancellation tests | Not observed |
| Missing URL, HTTP 403, permission denial, bad format and limits differ | Typed network/image/decoder/fetcher tests and image presentations | Not observed |
| Single-image retry does not reload/install the plugin | `MediaImage` attempt state and image request/cache-policy tests | Not observed |
| Closing/reopening a works session cannot restore stale query text over new default results | Session-keyed `MediaBrowserRoute` composition and browser saved-state removal; nested Back retains the live controller state | Not observed |
| Cover needing approved request headers, if evidence confirms this requirement | No SDK field added; see conditional work below | Conditional and unconfirmed |
| Narrow/wide/dark/large-font/keyboard/screen-reader behavior | Existing tokens, adaptive grids, semantics, separate Info, BackHandlers and saved positions | Not observed |

## File map

Core implementation:

- `evilo-core/src/commonMain/kotlin/crow/evilo/feature/browse/PluginHomeState.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/feature/browse/PluginHomeStore.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/feature/browse/BrowseProjection.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/app/AppPluginController.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/app/PluginSessionConnector.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/app/PluginSessionFactory.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/architecture/LatestOperation.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/plugin/catalog/PluginCatalog.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/plugin/catalog/PluginCatalogRepository.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/plugin/catalog/PluginBlock.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/storage/EviloPreferences.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/storage/EviloPreferencesRepository.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/feature/reader/MediaBrowserController.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/feature/reader/ImageFailure.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/network/ktor/NetworkFailures.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/network/ktor/BoundedHttpTransport.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/network/ktor/PluginNetworkPolicy.kt`
- `evilo-core/src/commonMain/kotlin/crow/evilo/network/ktor/PolicySourceHttpClient.kt`

App implementation:

- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginHomeScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginSourcesScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/SourceCatalogScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginDetailScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginDialogs.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginNavigation.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginMoreScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/BrowseUi.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/settings/SettingsScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/feature/reader/MediaBrowserScreen.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/app/EviloImages.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/app/PluginImageDecoder.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloAsyncImage.kt`
- `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloImageError.kt`
- `evilo-app/src/commonMain/composeResources/values/strings.xml`
- `evilo-app/src/commonMain/composeResources/values-zh/strings.xml`

Written tests, all unexecuted:

- `evilo-core/src/commonTest/kotlin/crow/evilo/feature/browse/BrowseProjectionTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/feature/browse/PluginHomeStoreTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/app/AppPluginControllerTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/architecture/LatestOperationTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/feature/reader/MediaBrowserControllerTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/feature/reader/ImageFailureTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/plugin/catalog/PluginCatalogRepositoryTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/storage/EviloPreferencesRepositoryTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/network/ktor/PluginNetworkPolicyTest.kt`
- `evilo-core/src/commonTest/kotlin/crow/evilo/network/ktor/BoundedHttpTransportTest.kt`
- `evilo-app/src/commonTest/kotlin/crow/evilo/app/PluginImageDecoderTest.kt`
- `evilo-app/src/jvmTest/kotlin/crow/evilo/app/EviloImagesTest.kt`

## Conditional work and remaining evidence

1. Run core and app JVM tests only after explicit authorization. Test compilation
   is also unobserved. Existing JDK/SDK/dependency/signing configuration prerequisites
   in the README still apply; this work does not resolve or change those versions.
2. Platform builds and UI/Back/focus/accessibility checks need separate authorization
   and their platform prerequisites. A JVM unit-test result would not establish
   Android, browser, iOS, large-font rendering, or screen-reader behavior.
3. Obtain the actual platform and redacted image error. Preserve real HTTP status,
   response type, final-address/redirect behavior and approved-header evidence;
   do not copy credentials, cookies, signed query values or private paths.
4. `MediaCatalog` and `MediaDetails` still pass `emptyMap()` for covers because
   `MediaItem` only has `coverUrl`. If evidence confirms a need for cover headers,
   design an optional default-empty SDK field, host/legacy-adapter serialization,
   and protocol minor/capability behavior before implementing it. Never hardcode
   provider headers or forward ambient cookies. Browser forbidden-header/CORS
   restrictions remain even after a portable field is added.
5. No offline plugin loading, automatic updates, theme/font switches, disk-cache
   cleanup, bookshelf/history/downloads, account sync, new trust authority, or
   upstream Wasmline changes were added. Extreme image aspect ratios keep the
   existing layout bound while Fit avoids cropping; unrestricted tall-page layout
   is not claimed.

Manual commands, **not run**:

```sh
./gradlew :evilo-core:jvmTest :evilo-app:jvmTest
```

Use the README's separate platform commands only when that exact build/run/device
operation is requested. The implementation goal is not marked complete on the
basis of file edits or written tests alone.
