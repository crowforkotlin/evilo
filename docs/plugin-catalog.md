# Plugin catalog format

The canonical bundled catalog is `plugin-repository/official.json`. The application
generates its bundled Kotlin data from that file; there is no second checked-in
copy. `plugin-repository/catalog.schema.json` describes schema 1 for editors.
`CatalogParser` is authoritative for runtime URL and cross-field checks.

The configured official refresh URL is:

```text
https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/official.json
```

This is derived from the application's repository remote, not an observed hosted
release. The bundled catalog intentionally lists no plugins until signed packages,
matching source archives, and source snapshots have been published. Other authors
may host their own JSON file and subscribe to it in Browse's source-management
action. Adding a catalog never adds a signing key.

## Install from a published catalog

`official.json` is a list of plugin releases, not a plugin binary. With
`"plugins": []`, it offers no official plugin to install. Publishing a source
repository alone does not provide an installable catalog or signed package.

1. In Browse, open Plugin Sources with the App Bar's `+` action.
2. Paste a publisher's direct JSON catalog URL and subscribe. Do not enter a
   repository homepage, source archive, individual WLM/Wasm URL, or signing key.
3. Choose the plugin's content type. Enable the NSFW preference in More only if
   you want to show entries with that classification.
4. Open the plugin, choose a compatible release, review its source and requested
   permissions, and confirm installation. Use Open to access an installed plugin.

Each listed release must already have a reachable signed manifest and all of its
referenced artifacts. The signature must match the application's preserved
authority; neither a custom catalog nor user input can replace it. Source,
archive, and icon URLs must describe the actual release. Downloads must be served
directly, without redirects, under the approved URL policy.

The JSON example in this document is illustrative. Its URLs do not provide a
working installation. Keep an official list empty rather than inserting guessed
URLs or claiming that an unpublished package can be installed.

## Catalog fields

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Required integer `1`; unknown schema versions are rejected. |
| `id`, `name`, `description` | Publisher identity and display text. A catalog ID does not select a trust key or grant official status. |
| `updatedAt` | ISO-8601 timestamp with an offset, preferably UTC `Z`. |
| `plugins` | Up to 512 entries with unique plugin IDs; an empty list is valid. |

IDs use lowercase letters, digits, and separated `.`, `_`, or `-` components and
start with a letter. Unknown optional JSON fields are ignored. Required fields
must remain compatible with schema 1; incompatible changes need another schema.
The whole UTF-8 JSON document must fit within 2 MiB.

## Plugin fields

| Field | Meaning |
| --- | --- |
| `id`, `name`, `authors` | Stable signed plugin ID, display name, and 1 to 16 authors. Each author has `name` and an optional HTTP(S) `url`. |
| `overseas` | Publisher-declared overseas availability, not a geolocation test or proxy setting. |
| `languages` | 1 to 32 distinct language tags such as `zh`, `zh-Hans`, or `en`. |
| `kinds` | Nonempty subset of `manga`, `novel`, `anime`, and `film`; one plugin may support several. |
| `nsfw` | Publisher-declared adult content. Such entries are hidden by default and may be shown in More. |
| `description` | Introduction, at most 8192 characters. |
| `sourceUrl` | Required source repository URL, not a `.wlm`, `.wasm`, `.pwasm`, or `.cwasm` download. |
| `iconUrl` | Required HTTP(S) icon URL. The current decoder supports PNG, JPEG, and WebP, not remote SVG. |
| `version` | Must equal the release with the highest `versionCode`, including a currently unusable newest release. |
| `createdAt`, `updatedAt` | ISO-8601 timestamps; update cannot precede creation. |
| `forceUpdate` | Defaults to `false`. When true, versions below the newest listed version code are blocked, not silently replaced. |
| `minimumUsableVersionCode` | Defaults to `1`; a minimum version floor independent of host compatibility. |
| `certified` | Defaults to `false`. Only the fixed official subscription may display this as official certification. It is not a safety guarantee. |
| `availableVersions` | 1 to 64 releases, with distinct version strings and version codes. |

Top-level `kinds` and `languages` describe the plugin as a whole. Each release
declares its own supported subset; this allows a newer release to add a content
type without making an older installation claim to support it.
Known NSFW classification updates also apply to existing installations. Opening
such a plugin requires enabling the preference; this relies on publisher metadata,
not automated content inspection or a parental-control guarantee.

## Release fields

| Field | Meaning |
| --- | --- |
| `version`, `versionCode` | Three-component version, optionally with a prerelease suffix, and a positive, increasing integer code. Codes determine ordering; strings are labels. |
| `kinds`, `languages` | Required release-specific kinds and languages; all must appear in the plugin's declarations. |
| `manifestUrl` | Required direct `.wlm` URL, without query parameters. Publish all referenced content-addressed artifacts beside it. |
| `sourceUrl` | Required immutable source snapshot link for this release. |
| `sourceRevision` | Required full lowercase Git commit: 40 or 64 hexadecimal characters, never a branch name. |
| `sourceArchiveUrl` | Required direct source `.zip` or `.tar.gz` URL, without query parameters. It must contain source, not just plugin binaries. |
| `sourceArchiveSha256` | Required 64-character lowercase SHA-256 of the exact archive bytes. |
| `publishedAt` | Timestamp between the plugin's creation and update dates. |
| `usable` | Defaults to `true`; false releases are not selectable or openable from this catalog. Withdrawing a release also blocks its installation after refresh. |
| `minimumHostVersionCode` | Defaults to `1`, compared with the application's integer version code, not the SDK version. |
| `maximumHostVersionCode` | Optional inclusive upper bound; null means no upper bound. |
| `protocol` | Major/minimum minor and required/optional capabilities; omitted fields use SDK defaults. |
| `permissions` | Separate API/media network approvals plus opt-in plugin key/value storage. |

Release identity, kinds, languages, protocol requirements, and permissions must
match `evilo.descriptor` in the signed manifest and the guest's runtime descriptor.
`evilo.sourceRevision` and `evilo.sourceArchiveSha256` in the signed manifest must
match the catalog. The descriptor fits Wasmline's default 16 KiB metadata-string
limit. Catalog descriptions, certification, dates, and link destinations are not
signed claims. The host does not download and inspect source archives or perform
a reproducible-build comparison; the signing operator must review the actual
archive and ensure the linked revision contains the released source. The template
exports actual source files and signs their archive hash, rather than exporting
a binary-only ZIP as source.

## Permissions and compatibility

Each `permissions.api` and `permissions.media` object has `origins`, `pathPrefixes`,
`methods`, and lowercase `headers`. Origins are exact HTTP(S) origins with no path,
query, credentials, or wildcard. Prefixes are absolute paths without traversal or
encoded delimiters. API requests support GET, HEAD, POST, PUT, PATCH, and DELETE;
only POST, PUT, and PATCH may have a body, bounded at 64 KiB. Media uses GET only.
Cookies, redirects, and ambient credentials are disabled. Plugins cannot supply
forbidden transport headers; the HTTP engine may add standard protocol headers.
Requesting a permission does not grant it before consent.

Protocol 1.0 capabilities currently include `media.catalog.v1`, `http.request.v1`,
and `storage.kv.v1`. A missing required capability or a different protocol major
rejects the release; an unknown optional capability does not. Requiring storage
also requires `permissions.storage=true`. A capability describes what the host
implements; the approved origins and storage flag determine what this plugin may
actually access.

The repository recommends the highest usable, compatible release for the selected
content type. It never falls back below a known mandatory floor. Version floors
are persisted and cannot be lowered by a later catalog refresh. A known official
floor also applies to copies of the same signed plugin ID in custom catalogs,
even when the official subscription is disabled. A custom source's floor is
scoped to that subscription so it cannot block another source's plugins. Removing
a custom source removes its scoped floor, cache, and installations; plugin data
is retained. Install/update still requires explicit permission review. An
incompatible mandatory release requires updating the application, not installing
an older plugin.

HTTPS is preferred. Plain HTTP needs explicit source consent; signatures remain
mandatory. Package artifacts must stay in the manifest's origin and standard
`artifacts/sha256/<prefix>/<hash>.<format>` directory. Host-controlled HTTP does
not bypass browser CORS or HTTPS-page mixed-content restrictions. Source-code
links and playback opened in an external application use that application's networking rules,
not evilo's download policy.

## Illustrative entry

These URLs, commit, and hash are illustrative, not published or officially
certified releases. Replace them with real immutable values before distribution.

```json
{
  "schemaVersion": 1,
  "id": "example.catalog",
  "name": "Example catalog",
  "description": "Publisher-maintained plugins; certification does not guarantee safety.",
  "updatedAt": "2026-11-09T00:00:00Z",
  "plugins": [{
    "id": "example.reader",
    "name": "Example reader",
    "authors": [{"name": "Example author", "url": "https://code.example.com/author"}],
    "overseas": true,
    "languages": ["zh", "en"],
    "kinds": ["manga", "novel"],
    "nsfw": false,
    "description": "A catalog-format example, not a hosted content provider.",
    "sourceUrl": "https://code.example.com/reader",
    "iconUrl": "https://plugins.example.com/reader/1.0.0/icon.png",
    "version": "1.0.0",
    "createdAt": "2026-11-08T00:00:00Z",
    "updatedAt": "2026-11-09T00:00:00Z",
    "forceUpdate": false,
    "minimumUsableVersionCode": 1,
    "certified": false,
    "availableVersions": [{
      "version": "1.0.0",
      "versionCode": 1,
      "kinds": ["manga", "novel"],
      "languages": ["zh", "en"],
      "manifestUrl": "https://plugins.example.com/reader/1.0.0/manifest.wlm",
      "sourceUrl": "https://code.example.com/reader/tree/1111111111111111111111111111111111111111",
      "sourceRevision": "1111111111111111111111111111111111111111",
      "sourceArchiveUrl": "https://plugins.example.com/reader/1.0.0/sources.zip",
      "sourceArchiveSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "publishedAt": "2026-11-09T00:00:00Z",
      "usable": true,
      "minimumHostVersionCode": 1,
      "maximumHostVersionCode": null,
      "protocol": {
        "major": 1, "minimumMinor": 0,
        "requiredCapabilities": ["media.catalog.v1", "http.request.v1"],
        "optionalCapabilities": []
      },
      "permissions": {
        "api": {"origins": ["https://api.example.com"], "pathPrefixes": ["/content/"], "methods": ["GET"], "headers": ["accept", "user-agent"]},
        "media": {"origins": ["https://images.example.com"], "pathPrefixes": ["/"], "methods": ["GET"], "headers": ["accept", "user-agent"]},
        "storage": false
      }
    }]
  }]
}
```
