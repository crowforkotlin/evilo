# Plugin catalog format

## Host your own plugin source

A plugin source is a JSON document hosted by its publisher. Evilo supplies no
default source or plugin: a fresh app has no subscriptions, and restart never
recreates a removed source. Users add source URLs explicitly. No pull request to
evilo, source registration, or particular filename is required.

1. Create a catalog using the compact example below. The root only requires
   `name` and `plugins`; `{"name":"My plugins","plugins":[]}` is a valid empty source.
2. Publish the JSON on your static site, object storage, or HTTP(S) endpoint.
   The URL must return the JSON directly, not a repository page or a redirect.
   `plugin.json`, `plugins.json`, any other name, and URLs without `.json` work.
3. In evilo, open Browse's Plugin Sources action, choose Add plugin source, paste
   that URL, and subscribe. Open its source catalog, choose a plugin's details,
   review a compatible release, and install. Subscription alone
   does not create installed Browse entries or execute the listed plugins.

A provider repository's `:exportPluginRelease` task exports a directly usable
`catalog.json`, signed manifest, artifacts, and actual source archive/hash. You may
publish that generated JSON unchanged. For a curated source, copy its release
metadata into `availableVersions` and keep common fields once per plugin. Hashes
and source revisions must come from the actual export, not invented values.

Every installable package must be signed. Official packages use the App's embedded
verification key; third-party publishers keep their own private key and publish
`signing` and `manifestSha256` on each release. A catalog declaration is not trust:
users explicitly approve the publisher fingerprint and exact version before
execution. No PR adding a publisher key to evilo is required. Catalog
inclusion is not a safety review.

```json
"signing": {"algorithm": "Ed25519", "keyId": "publisher-example", "publicKeyHex": "<64 lowercase hexadecimal characters>"},
"manifestSha256": "<SHA-256 of the exact signed manifest.wlm bytes>"
```

The placeholders are illustrative, not valid release metadata. Export uses the
actual publisher public key and signed manifest bytes. Compare fingerprints through
an independent publisher channel; initial confirmation alone cannot authenticate
a real-world identity. Unsigned packages and invalid signatures remain rejected.

There is no separate JSON Schema file to maintain and no `$schema` property to
add. `CatalogParser` normalizes compact input and then validates the existing
typed catalog, URL policies, and release relationships.


## Collect many repositories under one address

A plugin source describes plugins. A **subscription** describes sources, so one
address can bring in many publishers without the user pasting each URL.

Two document shapes are recognised by their fields, so an existing plugin catalog
keeps working unchanged:

| Shape | Declares | Meaning |
| --- | --- | --- |
| Plugin source | `plugins` | The document itself lists installable plugins |
| Subscription pointer | `indexUrl` | A signed index that already lists every plugin |

A document must declare exactly one of the two; declaring both or neither is rejected.
A source address never costs one request per plugin: a pointer's index already holds
every entry, including its newest release.

### Subscription pointer

```json
{
  "name": "Awesome evilo plugins",
  "indexUrl": "https://example.com/index.json",
  "signingKeyFingerprint": "<64 lowercase hexadecimal characters>",
  "keyId": "subscription"
}
```

The app fetches three files beside the index:

| File | Contents |
| --- | --- |
| `index.json` | Every plugin of every listed repository, already merged |
| `index.json.sig` | Ed25519 signature over the exact `index.json` bytes, 128 hex characters |
| `index.json.pub` | The signing public key, 64 hex characters |

Two checks run before any plugin is listed:

1. `SHA-256(index.json.pub)` must equal `signingKeyFingerprint`.
2. The signature must verify against `index.json.pub` over the exact bytes.

A host that swaps the index, the signature, or the public key therefore fails
unless it also controls the fingerprint the user subscribed to. One thousand
repositories cost four requests: the pointer, index, signature and public key.

Each index entry records the plugin's own `catalogUrl` and the plugin object a
catalog would publish, narrowed to its newest release. The list renders from that
object, and installation works from it too, so a subscriber does not fetch every
plugin catalog first. Older versions stay in the plugin's own catalog, which
remains authoritative for its full release history.

### Local development on Android

`127.0.0.1` in any document points to the phone, not the computer serving it.
For LAN access, publish an `indexUrl` using the computer's reachable LAN address;
serve the matching `.sig` and `.pub` files there too. Any plugin download URLs
inside the signed index must also be reachable from the phone. Changing the
index bytes requires regenerating its signature; changing only the pointer's
`indexUrl` does not.

Alternatively, keep computer-local URLs and run `adb reverse tcp:8080 tcp:8080`
for a server on port 8080. Add the pointer using `http://127.0.0.1:8080/repo.json`.
The app does not rewrite declared hosts. Enable HTTP source consent for either
HTTP development setup, and allow the Android local network permission when
prompted for LAN access.

### Publishing a subscription

The index is derived data, not hand-maintained. `evilo-subscribe-template` holds
the reference implementation:

- `sources.txt` — one plugin repository URL per line, the only hand-edited input
- `scripts/build_index.py` — fetches every source, merges the records, signs the index
- `.github/workflows/build-index.yml` — runs the build on push and commits the outputs

Because the index is rebuilt from scratch, a repository that publishes a new
version, changes a description, or withdraws a release is reflected on the next
build without editing the index.

## Compact example

The URLs, revision, and hash below are illustrative, not published releases.
Replace release fields with real export results before distribution.

```json
{
  "name": "My plugins",
  "plugins": [
    {
      "id": "example.reader",
      "name": "Example reader",
      "authors": ["Example author"],
      "description": "A catalog-format example.",
      "languages": ["en"],
      "kinds": ["manga"],
      "sourceUrl": "https://code.example.com/reader",
      "availableVersions": [
        {
          "version": "1.0.0",
          "versionCode": 1,
          "publishedAt": "2026-11-09",
          "manifestUrl": "https://plugins.example.com/reader/1.0.0/manifest.wlm",
          "sourceRevision": "1111111111111111111111111111111111111111",
          "sourceArchiveUrl": "https://plugins.example.com/reader/1.0.0/sources.zip",
          "sourceArchiveSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        }
      ]
    }
  ]
}
```

Authors may be strings or objects such as
`{"name":"Example author","url":"https://code.example.com/author"}`. An omitted
icon displays the plugin name's initial. Dates may be `YYYY-MM-DD` or full
ISO-8601 timestamps with an offset; ordinary dates mean midnight UTC.

Each release inherits omitted `kinds`, `languages`, `sourceUrl`, and `protocol`
from its plugin. An explicit release field replaces the entire common field,
without merging objects or lists. Missing members use SDK defaults, not
common-field members.

## Update a source

Keep the subscription URL stable and append a release to `availableVersions`
with a new version and higher `versionCode`. Copy its manifest URL, actual source
revision/archive/hash, and publication date from export. The plugin's current
version and creation/update dates, and the source's update date, are derived
unless explicitly supplied. Users receive the new listing when the app refreshes.

Do not reuse version codes or silently replace old release metadata. Changing
common `protocol`, `kinds`, `languages`, or `sourceUrl` also changes older releases
that inherit them. When a new release differs, retain explicit overrides for older
releases or override the new release instead. Approved installations keep their
recorded metadata; changed normalized release metadata blocks opening and requires
review, and signed descriptor checks still apply.

Each third-party version requires a new explicit approval. A changed signing key
is a publisher change, not a transparent update. Installed loads use their recorded
verification identity and signed-manifest digest. Opens prefer cached signed bytes,
still rechecking their signature and digest. A missing cache fetches again; changed
remote bytes are rejected even when the same publisher signs them. Cached opens do
not poll for remote replacements. Publishers must issue a new version/code for
changed code. Legacy installations without a pinned digest require
one explicit reinstall; original timestamps are retained for the unchanged release.
No global third-party key whitelist or unsigned loading path is introduced.

Removing a release or marking it `usable: false` withdraws it after refresh;
existing installations of that release can no longer open from that source.

## Catalog fields

| Field | Meaning |
| --- | --- |
| `name` | Required source display name, at most 120 characters. |
| `plugins` | Required list of up to 512 entries with unique plugin IDs; an empty list is valid. |
| `id` | Optional catalog label, defaults to `catalog`. Subscriptions have their own app-assigned IDs, so independent sources may omit this. |
| `description` | Optional source introduction, defaults to an empty string. |
| `updatedAt` | Optional date, defaults to the latest plugin update date; an empty source defaults to `1970-01-01T00:00:00Z`. |
| `schemaVersion` | Optional format-compatibility integer, defaults to `1`; other values are rejected. Not an external schema reference. |

IDs use lowercase letters, digits, and separated `.`, `_`, or `-` components and
start with a letter. Unknown optional JSON fields are ignored. Compact and full
catalogs share format version 1. The whole UTF-8 document must fit within 10 MiB.

## Plugin fields

| Field | Meaning |
| --- | --- |
| `id`, `name`, `authors` | Required stable signed plugin ID, display name, and 1 to 16 author names/objects. |
| `description` | Required introduction, at most 8192 characters. |
| `languages` | Required 1 to 32 distinct language tags such as `zh`, `zh-Hans`, or `en`; releases inherit it. |
| `kinds` | Required nonempty subset of `manga`, `novel`, `anime`, and `film`; releases inherit it. |
| `sourceUrl` | Required source repository URL, not a `.wlm`, `.wasm`, `.pwasm`, or `.cwasm` download. |
| `availableVersions` | Required 1 to 64 releases, with distinct version strings and version codes. |
| `protocol` | Optional common release declaration. Without it, SDK defaults require protocol 1.0 with `media.catalog.v1`. |
| `overseas` | Defaults to `false`; publisher-declared overseas availability, not geolocation or a proxy setting. |
| `nsfw` | Defaults to `false`; publisher-declared adult content, hidden unless enabled in More -> Settings -> Browse and content. |
| `iconUrl` | Optional HTTP(S) PNG/JPEG/WebP icon; omission or an empty string uses the initial-letter placeholder. Remote SVG is unsupported. |
| `version` | Defaults to the release with the highest `versionCode`, even if unusable; an explicit value must match. |
| `createdAt`, `updatedAt` | Default to earliest/latest release publication dates; explicit dates must contain every release date. |
| `forceUpdate` | Defaults to `false`. When true, versions below the newest listed version code are blocked, not silently replaced. |
| `minimumUsableVersionCode` | Defaults to `1`; a minimum version floor independent of host compatibility. |

Plugin-level `kinds` and `languages` describe the plugin as a whole. Releases may
override them with a supported subset, allowing a new release to add a content
type without changing an older release's signed descriptor.
Known NSFW classification updates also apply to existing installations. Opening
such a plugin requires enabling the preference; this relies on publisher metadata,
not automated content inspection or a parental-control guarantee.
For installed copies of the same signed plugin ID, classification conservatively
includes their recorded declarations and their current source metadata, even if
one installed source is disabled. An uninstalled catalog's same-ID claim is not an
installed copy and does not silently replace its data.

## Release fields

| Field | Meaning |
| --- | --- |
| `version`, `versionCode` | Required three-component version, optionally with a prerelease suffix, and a positive, increasing integer code. Codes determine ordering; strings are labels. |
| `manifestUrl` | Required direct `.wlm` URL, without query parameters. Publish all referenced content-addressed artifacts beside it. |
| `sourceRevision` | Required full lowercase Git commit: 40 or 64 hexadecimal characters, never a branch name. |
| `sourceArchiveUrl` | Required direct source `.zip` or `.tar.gz` URL, without query parameters. It must contain source, not just plugin binaries. |
| `sourceArchiveSha256` | Required 64-character lowercase SHA-256 of the exact archive bytes. |
| `publishedAt` | Required ordinary date or timestamp between the plugin's creation and update dates. |
| `kinds`, `languages` | Inherit plugin declarations; explicit values must be nonempty subsets of them. |
| `sourceUrl` | Inherits the plugin repository URL. May override it with this release's immutable source snapshot link; revision and archive/hash still identify the exact source. |
| `usable` | Defaults to `true`; false releases are not selectable or openable from this catalog. Withdrawing a release also blocks its installation after refresh. |
| `minimumHostVersionCode` | Defaults to `1`, compared with the application's integer version code, not the SDK version. |
| `maximumHostVersionCode` | Optional inclusive upper bound; null means no upper bound. |
| `protocol` | Inherits plugin requirements; otherwise SDK defaults. Explicit objects replace the common object and use SDK defaults for omitted members. |
| `signing` | Optional publisher Ed25519 `keyId` and 32-byte lowercase `publicKeyHex`; may inherit from the plugin. Omission only supports legacy official-signed releases. Never automatically trusted. |
| `manifestSha256` | Exact signed manifest SHA-256; required with `signing`. Installation records pin the verified digest, including for legacy official releases. |

Release identity, kinds, languages, and protocol requirements must match
`evilo.descriptor` in the signed manifest and the guest's runtime descriptor.
`evilo.sourceRevision` and `evilo.sourceArchiveSha256` in the signed manifest must
match the catalog. The descriptor fits Wasmline's default 16 KiB metadata-string
limit. Catalog descriptions, dates, and link destinations are not
signed claims. The host does not download and inspect source archives or perform
a reproducible-build comparison; the signing operator must review the actual
archive and ensure the linked revision contains the released source. A provider
repository's exporter includes actual source files and signs their archive hash,
rather than exporting a binary-only ZIP as source.

## Network and compatibility

The host imposes no origin allowlist and no per-plugin capability declaration.
Installing a plugin is the authorization decision; the plugin may request any
absolute HTTP(S) host, method, path, and header set it needs. The host still
enforces:
- HTTPS unless the subscribing catalog was added with explicit cleartext consent;
- no URL credentials and no path traversal;
- at most 32 request headers of at most 4096 characters, with cookies, `Host`,
  `Connection`, `Content-Length`, `Origin`, and forwarding headers rejected;
- request bodies of at most 64 KiB and per-call response byte limits.

Redirects are not followed. The HTTP engine may add standard protocol headers.
Plugin key/value storage is always available, isolated by publisher fingerprint
and signed plugin ID, with limits of 1 MiB total, 256 entries, 64 KiB per value,
and 128-character keys.

Protocol 1.0 capabilities currently include `media.catalog.v1`, `http.request.v1`,
and `storage.kv.v1`. A missing required capability or a different protocol major
rejects the release; an unknown optional capability does not. A capability
describes what the host implements, not a per-plugin grant.

The repository recommends the highest usable, compatible release for the selected
content type. It never falls back below a known mandatory floor. Version floors
are persisted and cannot be lowered by a later catalog refresh. Every source's
floor is scoped to that subscription; it cannot block another source's copy of
the same signed plugin ID. Disabling a source retains its floors. Every source
can be removed. Startup also removes the known historical automatic source
ID-and-URL pairs, retaining their installations/KV for explicit management; manually
added sources on the same host remain. Removing a source removes its scoped floor, cache, and
installations; plugin data is retained. Install/update still requires explicit
release review.
