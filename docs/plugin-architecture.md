# Plugin architecture

## Repository ownership

`evilo` owns the application, the protocol SDK, catalog format, signing authority,
network approvals, persistence, and Wasmline loading. It contains no source-site
implementation or guest binary. Source-site implementations and release files
belong in independent `evilo-plugin-<provider>` repositories, not host code,
resources, build configuration, or documentation. `evilo-plugin-template` is an
independent, provider-neutral Gradle build. A plugin is never a project dependency
of the application.

The SDK is maintained once in `evilo-library`, configured for publication as
`io.github.crowforkotlin.evilo:evilo-library`. Plugins pin an SDK release. An explicit
local composite may substitute this same module during development; no protocol
source is copied into the template. A third repository is unnecessary until the
SDK needs independent ownership or release management.
The configured SDK version is `1.0.0`; these changes do not establish remote
artifact availability. Use the explicit local composite before publication.

The host has three shared modules: `evilo-app` contains every Compose page,
theme/resource, Coil loader, and image header limits; `evilo-core` contains catalogs, network,
storage, loading, sessions, and controllers; `evilo-library` contains portable
models and the plugin/host protocol. Dependencies flow app to core to library.
Sessions return permission-checked image bytes rather than Compose types.
The app injects its generated public trust authority into the core session
factory. Existing model/service packages and JSON envelopes remain unchanged.
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

## Signing authority

The application build generates one Ed25519 authority pair outside both
repositories. Only its public key is compiled into the application. The template
uses that authority's private-key file and reads its public authority metadata;
the evilo-app's current key ID is `evilo-root-v1`. It never generates a replacement key.
Packages signed by another authority are rejected.
Users cannot enter public keys, and catalogs cannot add trusted keys.

This is an application-release authority, not a different key for every phone.
Per-installation keys could not verify the same publicly distributed package.
Release builders must preserve the authority pair across application releases.
Independent developers submit source and artifacts to the authority operator for
signing; the private key must not be shared or committed. A development
build using another authority cannot load packages from the release authority.

A valid signature identifies the signing authority; it does not prove that code
is harmless. Catalog metadata cannot grant network/storage access. Every
installation shows the release's permissions and requires explicit consent.

## Catalogs and releases

The application bundles `plugin-repository/plugin.json`. Adding a source only
requires a URL whose response is catalog JSON; filenames and a `.json` suffix
are not required. Publishers may host independent sources without a PR to evilo.
The root only needs `name` and `plugins`; no external JSON Schema is used.
Catalogs describe authors, region, languages, media kinds, NSFW, introduction,
optional icon, source repository, releases, compatibility, and forced updates.
`CatalogJson.kt` expands common plugin fields into releases and derives omitted
version/date summaries before `CatalogParser` validates the full typed metadata.
Authors may be strings and dates may use `YYYY-MM-DD`.

Each release inherits omitted kinds, languages, source URL, permissions, and
protocol; explicit overrides replace whole fields. Every release supplies a
`.wlm` URL, immutable source revision, actual source archive URL/hash, version/code,
and publication date. A source URL may inherit the repository link or override
it with an immutable snapshot. A link ending in a Wasm binary or manifest is not
accepted as a source-code link. Changing defaults can change older inherited
release metadata; approved installation permissions are never silently replaced.

The bundled catalog starts empty until an actual signed package and its
matching source release are published. No guessed release URL is presented as an
installable package. The template exports a full catalog entry together
with a source archive when the publisher supplies real release URLs.
`docs/plugin-catalog.md` documents independent hosting and the compact format.
The signed manifest binds the runtime descriptor, source revision, and
archive hash. An identity-checking network wrapper compares those declarations
before Wasmline creates the runtime; unchanged bytes still undergo upstream
signature/artifact-hash verification. The wrapper is not a signature verifier.
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
their source. A built-in source's floor does not block copies of the same signed
plugin ID in other subscriptions. The built-in source can be disabled, not
removed. Removing a custom source removes its cache,
installations, and scoped floors, but preserves plugin data. An uninstall erases
plugin data only after its last installation across subscriptions is removed.

HTTPS is preferred. A user may explicitly approve an HTTP catalog/package origin
after a cleartext warning. This does not disable signature verification, permit
redirects, or grant a plugin arbitrary URLs. Web mixed-content and CORS policies
still apply. Android/iOS cleartext platform settings are paired with explicit
host URL policies, not unrestricted plugin networking.
Dynamic user-approved HTTP origins cannot be enumerated in static Android network
security or iOS ATS domain lists, so platform cleartext support is enabled and
the host applies the per-source/per-release runtime restrictions. This broadens
platform transport capability, not the plugin's signed permissions.

## Resource limits and remaining risks

The host bounds catalog/manifest downloads at 2 MiB, artifacts at 64 MiB, API
responses at 2 MiB, images at 16 MiB, and request bodies at 64 KiB. Requests have a
30-second total timeout and at most four simultaneous exchanges in the one
application-owned Ktor transport.
Manifest reuse is disabled (`manifestTtlMs = 0`); each open fetches a signed manifest
and performs signature/descriptor checks. The loader's default artifact cache is
enabled instead of `WasmlineNoOpCache`, with a configured native limit of 128 MiB.
Cached artifacts are checked against the size and SHA-256 declared by the signed
manifest before loading. This does not implement offline loading. Descriptors use upstream's default
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
Plugin key/value storage is durable, isolated by signed plugin ID,
bounded by key/value/count/total quotas, and available only after storage consent.
It is not a credential vault or a filesystem API. Plugins cannot choose their
namespace or access host preferences or another plugin's values.
The current quotas are 1 MiB of UTF-8 key/value bytes, 256 entries, 64 KiB per
value, and keys up to 128 characters. Versions/subscriptions of the same signed
plugin ID share that namespace. Storage migration belongs to the plugin; there
is no automatic account/login synchronization or encrypted credential storage.

DataStore `1.3.0` stores preferences and plugin namespaces as typed Protobuf, with
a 32 MiB aggregate limit. `PluginStorageSession.transaction` loads the namespace
before calling the guest, accepts synchronous reads/writes in staged memory, and
commits through `DataStore.updateData` only after the host operation succeeds.
The callback's `writeValue(true)` acknowledges a staged write, not synchronous
disk durability. Failure/cancellation before commit discards staged writes. Once
commit starts, it finishes under `NonCancellable` without releasing the operation
mutex early; a completed durable write cannot be rolled back by cancellation.
Per-ID mutexes serialize sessions; namespace revisions reject conflicting writes
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

Duplicate merging affects display only within the app's single signing authority.
Actions retain `sourceId:pluginId`, saved defaults/oldest installations determine
source choice, and unavailable choices require explicit selection without automatic
fallback. Floors and permissions are not merged; NSFW uses the strictest known
classification among installed copies. Details expose source declarations and
per-source uninstall, rather than deleting all same-ID copies through one button.

`HomePage` owns returnable settings/source/detail/browser routes; one `HomeOverlay`
owns selectors and permission/removal/uninstall confirmations. Approval rechecks
the exact reviewed release. Install-only closes its verification session and
retains its initiating page; install-and-browse reuses that session. Opening still
reacquires the remote signed manifest and is not an offline-loading guarantee.
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
