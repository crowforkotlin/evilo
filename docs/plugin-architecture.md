# Plugin architecture

## Repository ownership

`evilo` owns the application, catalog format, official-key configuration,
per-release publisher approval, persistence, and Wasmline loading. It contains no source-site
implementation or guest binary. Source-site implementations and release files
belong in independent `evilo-plugin-<provider>` repositories, not host code,
resources, build configuration, or documentation. `evilo-plugin-template` is an
independent, provider-neutral Gradle build. A plugin is never a project dependency
of the application.

The protocol SDK is published as `io.github.crowforkotlin.evilo:evilo-library`.
This repository pins that release in `gradle/libs.versions.toml`
(`evilo-plugin-sdk`) and consumes the artifact. Plugins pin the same coordinate;
no protocol source is copied into a provider repository. A local composite
(`-Pevilo.useLocalSdk`) substitutes the SDK during development.

The host has two shared Gradle modules plus the SDK artifact: `evilo-app` contains
every Compose page, theme/resource, Coil loader, and image header limits;
`evilo-core` contains catalogs, network, storage, loading, sessions, and
controllers; the `evilo-library` artifact contains portable models and the
plugin/host protocol. Dependencies flow app to core to library.
Sessions return host-fetched image bytes rather than Compose types.
The app injects its build-supplied official public verification key into the core
session factory. Reviewed third-party releases use only their declared key for
that release. Existing model/service packages and JSON envelopes remain unchanged.
`docs/application-architecture.md` describes the shared MVI stores, isolated Koin
application, Ktor transport, typed DataStore, and Room3 platform bindings.

## Protocol and extensibility

`EviloPluginService` and `EviloHostService` are synchronous Wasmline services.
Network work remains asynchronous in the host. `start` returns either a bounded
HTTP request with explicit context or a final result; `resume` receives that same
operation/context and the host response. Context is not sent over HTTP. Operations
support manga, novels, anime, and films through plugin-qualified media IDs.
Only chapter operations may accumulate intermediate batches. Service calls and
close are serialized; HTTP exchanges may suspend between calls. Cancellation is
propagated and replaced UI operations cannot publish stale results.

The fixed bridge uses JSON envelopes with unknown-field tolerance. Protocol major
changes reject incompatible plugins. Additive minor versions negotiate named
required and optional capabilities; unsupported required capabilities reject
loading. SDK artifact, protocol, plugin release, and application versions are
distinct. Legacy `MangaSourceService` API 2 remains an SDK compatibility contract,
but new application installations use the generic protocol, not API 2 packages.

The entrypoint only binds its plugin implementation. It must not call host
services, perform networking, or access storage during initialization. The loader
first verifies the signed manifest, binds a plugin-specific host service, then
links and negotiates the plugin. Host services must be looked up only when an
operation runs. Service parameters/returns cannot themselves be service contracts;
the upstream compiler rejects this, as well as suspend functions and overloads.
Changing a bridge method needs an explicit ABI compatibility decision, not just
an additive JSON field. Optional extension fields/capabilities must have defined
fallback behavior; unknown required capabilities fail before execution.

## Official and third-party signing

App compilation requires the official Ed25519 public key supplied through
`EVILO_PUBLIC_KEY_HEX` or an existing external `public.key`. The environment value
takes precedence; otherwise the file is read from `~/.evilo/signing/public.key`,
with its directory overridable by `-Pevilo.signingKeyDirectory=/absolute/external/directory`.
`GenerateEviloAppConfiguration` accepts a raw 32-byte hexadecimal public key,
normalizes it, and embeds only public material with key ID `evilo-root-v1`.
Missing or invalid public material fails the build. Compilation does not depend
on `generatePluginSigningKeys`, create an authority, or read a private key.
The separately invoked key-generation task is for official operators only, not
an automatic app-build prerequisite. Preserve the official verification key
across app releases; changing it invalidates legacy official signatures and may
change storage identity. See [README configuration](../README.md#sdk-signing-and-storage).

Official packages use the embedded public key. Third-party publishers sign with
their own private key and declare `signing` (`algorithm: Ed25519`, `keyId`, raw
32-byte lowercase `publicKeyHex`) and `manifestSha256` on each release. Signing
may inherit from the plugin, but the exact signed-manifest digest is required per
release. Neither catalog inclusion nor subscription grants trust. The install
review displays the exact version and publisher fingerprint; third-party
execution requires explicit acceptance of both. The fingerprint
is the lowercase SHA-256 of the raw public-key bytes, not the key ID or hexadecimal
text. Compare it through an independent publisher channel; initial acceptance
cannot establish a real-world identity.

`releaseVerificationKeys()` in `PluginPublisher.kt` confines a declared key to
that release and matching algorithm/key ID. It does not extend an app-wide trust
list. Omitted signing metadata is only the legacy official-key fallback, never an
unsigned-loading path. Wasmline signature verification remains mandatory for both
official and third-party packages; declared keys alone do not verify a signature.
Publishers keep private keys outside host/plugin repositories, resources, catalogs,
and source archives. Independent authors never need the official private key.

Installation records preserve the approved release's signing declaration and
SHA-256 of the exact verified signed manifest bytes. Installed loads use that
recorded key and digest, not a replacement from a refreshed catalog. Different
remote bytes are rejected even if signed by the same publisher; changed code needs
a new version/code and review. Legacy installations lacking a pinned digest are
blocked until explicit review/reinstall; an unchanged release keeps its original
timestamp. A changed publisher key requires uninstalling all existing same-ID
copies before installation, including disabled-source copies.

A valid signature identifies a key, not harmless code. Installing a plugin is
the authorization decision for the network and storage it uses. Every
installation requires explicit release approval; every new third-party version
requires publisher review.

## Catalogs and releases

The application supplies no default source or plugin and does not bundle
`plugin-repository/plugin.json`. Fresh storage has no subscriptions; initialization
restores only stored records and never recreates a removed source. Adding a source
requires a URL whose response is catalog JSON; filenames and a `.json` suffix
are not required. Publishers host their own sources without a PR to evilo.
The root only needs `name` and `plugins`; no external JSON Schema is used.
Catalogs describe authors, region, languages, media kinds, NSFW, introduction,
optional icon, source repository, releases, compatibility, and forced updates.
`CatalogJson.kt` expands common plugin fields into releases and derives omitted
version/date summaries before `CatalogParser` validates the full typed metadata.
Authors may be strings and dates may use `YYYY-MM-DD`.

Each release inherits omitted kinds, languages, source URL, and protocol;
explicit overrides replace whole fields. Every release supplies a
`.wlm` URL, immutable source revision, actual source archive URL/hash, version/code,
and publication date. A source URL may inherit the repository link or override
it with an immutable snapshot. A link ending in a Wasm binary or manifest is not
accepted as a source-code link. Changing defaults can change older inherited
release metadata; approved installation records are never silently replaced.

No guessed release URL is presented as an installable package. A provider
repository's exporter writes a full catalog entry together with a source archive
when the publisher supplies real release URLs.
`docs/plugin-catalog.md` documents independent hosting and the compact format.
The signed manifest binds the runtime descriptor, source revision, and
archive hash. An identity-checking network wrapper compares those declarations
and the expected exact manifest digest before Wasmline creates the runtime;
unchanged bytes still undergo upstream signature/artifact-hash verification.
The wrapper is not a signature verifier. Third-party declarations require a
catalog digest; legacy official catalogs may omit it, but installation still
records the verified bytes' digest and requires it for every later open.
Source-link availability, archive contents, and reproducible builds still require
publisher review; the host does not inspect source archives automatically.

JSON is not trusted code. Unknown optional fields permit catalog evolution;
unknown format versions, invalid URLs, duplicate IDs/version codes, binary-only
source links, and inconsistent releases are rejected. Revoked/unusable versions
known to the current catalog cannot be opened. A forced update blocks older
installations; an incompatible
mandatory release requires an application update rather than falling back to an
older version. Failed refreshes preserve prior data and display the failure.
Known minimum version floors are persisted monotonically and stay scoped to
their source and cannot block copies of the same signed plugin ID in other
subscriptions. Every source can be disabled or removed, including sources
retained from earlier automatically seeded startup data. Removing a source removes its cache,
installations, and scoped floors, but preserves plugin data. An uninstall erases
plugin data only after its last installation across subscriptions is removed.

HTTPS is preferred. A user may explicitly approve an HTTP catalog/package origin
after a cleartext warning. This does not disable signature verification or
permit redirects. Web mixed-content and CORS policies still apply.
Per-subscription cleartext consent cannot be enumerated in static Android network
security or iOS ATS domain lists, so platform cleartext support is enabled and the
host applies the subscription and request restrictions at runtime. Plugin requests
otherwise reach any HTTPS host they name; installing the plugin is the
authorization decision.

On Android 17 (API 37) and later, builds targeting API 37 or later also declare
and request `ACCESS_LOCAL_NETWORK`. `MainActivity` waits for the permission result
before starting `App`, so startup refreshes do not race the permission prompt.
Denial does not prevent using the app, but LAN sources remain unavailable until
the user allows Nearby devices in system settings. HTTP source consent does not
grant this Android permission.

## Resource limits and remaining risks

The host bounds catalog/manifest downloads at 10 MiB, artifacts at 64 MiB, API
responses at 2 MiB, images at 16 MiB, and request bodies at 64 KiB. Requests have a
30-second total timeout and at most four simultaneous exchanges in the one
application-owned Ktor transport.
The loader's URL/TTL manifest reuse is disabled (`manifestTtlMs = 0`) so its
network-client interface always passes bytes through the application's identity
check. `CachedPackageClient` supplies an installed release's exact signed bytes
from the separate host-only `PluginManifestCache`, addressed by the installation's
approved SHA-256. Every load repeats the digest, identity, provenance, descriptor,
and Wasmline Ed25519 signature checks. Cache hits report `VERIFYING_PACKAGE`, not
`FETCHING_MANIFEST`; artifacts keep their original URL base and network policy.
Missing or corrupt entries download again and still must match the approved digest.
New-release installs bypass this cache. Downloaded bytes are persisted only after
the signed runtime load succeeds and `AppPluginController` accepts/records the
installation, never from `onManifestChecked` alone. Cached opens do not poll for
remote manifest replacement. Catalog refresh still checks withdrawal and metadata.

Room database version 2 adds only `plugin_manifest_cache`; explicit migration
preserves sources, catalog caches, installations, floors, and guest data. The
manifest cache evicts least-recently-used entries above 16 MiB or 256 entries,
with a 2 MiB per-manifest limit. It is not exposed through guest storage.
The loader's default artifact cache remains enabled instead of `WasmlineNoOpCache`,
with a configured native limit of 128 MiB. Cached artifacts are checked against
the size and SHA-256 declared by the signed manifest before loading. Both caches
can eliminate package-network requests when present; eviction or missing artifacts
can still require downloads. API/search/content/images retain their network needs;
this is not an offline-media/download feature. Descriptors use upstream's default
16 KiB metadata-string limit. Plugin steps are bounded at 4 MiB, continuation
context at 128 KiB, HTTP exchanges at 512, chapters at 20,000, ordered images at
2000, and streams at 32.

PNG/JPEG/WebP dimensions are checked before decoding: image dimensions are limited
to 16,384 and total pixels to 33,554,432. Icons have separate 1 MiB, 512-pixel,
and 262,144-pixel bounds. Coil decodes the validated bytes and shares a 32 MiB
memory cache with weak references and disk caching disabled. A Coil interceptor
rechecks URL approval and session lifetime before memory-cache access and before
returning a result. Media keys include a session-specific prefix and a hash of
the approved URL/headers; closed sessions cannot reuse another session's cache.
Unknown image models and automatic network components are disabled. Remote SVG
is unsupported. `SessionImages.close()` cancels in-flight supervised image requests,
evicts their scoped entries, and exposes disconnection to the page. Individual
retry attempts bypass memory-cache reads while retaining approval checks; blank
URLs have no request. Typed HTTP/policy/format/limit/session failures drive compact
image states and redacted diagnostics without displaying original exception text.
Platform decoder failures after header validation are format errors, not HTTP errors.

`PluginNetworkPolicy.mediaRequest` supplies a generic `User-Agent: evilo` when a
plugin approves the `user-agent` media header and the request omits it. This also
applies to covers, whose `MediaItem` only carries a URL. Explicit image headers
are preserved and undergo the same approval and duplicate-header checks; API
headers and credentials are never copied into image requests.

The current upstream `WasmlineConfig` does not expose guest fuel, execution-time,
or linear-memory budgets. HTTP/coroutine timeouts cannot interrupt a synchronous
guest that loops or allocates excessively. Running native work off the UI thread
does not remove this denial-of-service risk, and browsers may still block their
main thread. Signing must not be described as complete isolation.
The initialization rule is a plugin-author contract, not proof that signed code
is harmless.

## Storage and interface

Host persistence stores source subscriptions, selected media kind, NSFW/duplicate
preferences, default installation choices, installations, and approvals.
Plugin key/value storage is durable, isolated by verified publisher and signed
plugin ID, bounded by key/value/count/total quotas, and available only after
storage consent. Official releases retain the legacy ID-only persistent key;
third-party keys are `publisher:<public-key fingerprint>:<plugin ID>`.
`PluginSessionFactory` selects that key through `publisherStorageKey()` and passes
it to `EviloPreferencesRepository.pluginSession(pluginId, storageKey)`. The guest
still accesses only `plugin.<plugin ID>` and cannot select the persistent key,
read host preferences, or access another publisher's retained values.
It is not a credential vault or a filesystem API.
The current quotas are 1 MiB of UTF-8 key/value bytes, 256 entries, 64 KiB per
value, and keys up to 128 characters. Versions/subscriptions of the same
publisher/ID share data; changing publisher does not migrate retained values.
Storage migration belongs to the plugin; there is no automatic account/login
synchronization or encrypted credential storage.

DataStore `1.3.0` stores preferences and plugin namespaces as typed Protobuf, with
a 32 MiB aggregate limit. `PluginStorageSession.transaction` loads the namespace
before calling the guest, accepts synchronous reads/writes in staged memory, and
commits through `DataStore.updateData` only after the host operation succeeds.
The callback's `writeValue(true)` acknowledges a staged write, not synchronous
disk durability. Failure/cancellation before commit discards staged writes. Once
commit starts, it finishes under `NonCancellable` without releasing the operation
mutex early; a completed durable write cannot be rolled back by cancellation.
Per-persistent-key mutexes serialize sessions; namespace revisions reject conflicting writes
from another app instance. Erasing a namespace increments its revision rather
than reusing it.
Runtime disposal waits for operations but does not depend on reading DataStore.

Room3 `3.1.0-alpha03` stores subscriptions, catalog JSON, installation metadata,
and mandatory version floors. KSP generates the database/DAO implementations on
all configured targets. Catalog changes persist in a DAO transaction before the
repository publishes them. Commit and state publication finish together even
when cancellation arrives during a database write. Room and DataStore do not
share a transaction: the last uninstall atomically records a pending data
deletion in Room, then erases the DataStore namespace. Startup, refresh, and
installation preparation retry unfinished cleanup. Removing a JSON source still
retains plugin KV.

Android uses private files, desktop uses app-data directories, and iOS uses
Application Support. JS/Wasm JS use DataStore's `WebOpfsStorage` and a custom
SQLite Worker with the persistent SAH pool VFS. The worker holds a Web Lock for
one tab per origin, never clears files on initialization, and preserves the pool
on initialization failure. It closes databases and releases handles before a
normal shutdown. HTTPS or localhost, OPFS, Workers, and Web Locks are required;
unsupported/private browser modes display a startup failure, not a silent memory
fallback. Browser quotas/eviction remain possible. Native multi-process and
cross-device synchronization are not implemented. Old JSON/localStorage formats
are removed without migration; this is first-release development.

The MD3 home has Browse and More destinations and a centered floating media-kind
selector. Browse contains source-grouped installed plugins, local search, source
filtering, and one App Bar source-management action. Main row clicks open works
directly; the independent Info action opens full details without loading a session.
Source catalogs provide All/Installed filtering and first-install review. More
lists Settings, Sources, Safety, and About; Settings owns content preferences and
raw installation management. Compact root pages use a bottom bar; expanded pages
use a rail. Secondary phone pages hide the bottom bar, and works/reading hides both.

Duplicate merging affects display only among verified same-ID/same-publisher
installations. `PluginCatalogRepository.releaseBlock()` blocks another publisher's
same-ID release until every installed copy of that ID is removed, even if its
source is disabled. Matching catalog IDs/names/icons do not grant identity.
Actions retain `sourceId:pluginId`, saved defaults/oldest installations determine
source choice, and unavailable choices require explicit selection without automatic
fallback. Floors are not merged; NSFW uses the strictest known
classification among installed copies. Details expose source declarations and
per-source uninstall, rather than deleting all same-ID copies through one button.

`HomePage` owns returnable settings/source/detail/browser routes; one `HomeOverlay`
owns selectors and approval/removal/uninstall confirmations. Approval rechecks
the exact reviewed release. Install-only closes its verification session and
retains its initiating page; install-and-browse reuses that session. Opening
recreates the signed runtime, preferring the pinned local manifest and cached
modules, but this is not a guarantee of offline content or cache availability.
An unchanged installed release retains its original record and timestamp.
Text and images have in-app viewing code. Videos open in an external application
after initial URL approval; custom HTTP headers are rejected. The external
application controls its subsequent network/redirect/credential behavior.

Implementation inspection is separate from runtime validation. Builds, tests,
device actions, and screenshots require an explicit user request.
Parser, compatibility, storage, update-floor, Browse projection/store, connection,
image-policy/session/decoder, and media-controller test sources
are present; they have not been executed for this redesign. No claim of successful
cross-platform compilation, loading, rendering, or live provider access is made.
Debug image details preserve the typed host rejection and a validated rejected
header name, without exposing its value; release hides these additional details.
Exact live requests remain unobserved. Cover-specific request headers and any
SDK/protocol extension require evidence; browser restrictions cannot be bypassed
by adding a field. See [the progress ledger](browse-plugin-plan-progress.md).
