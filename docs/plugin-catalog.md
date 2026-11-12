# Plugin catalog format

## Host your own plugin source

A plugin source is a JSON document hosted by its publisher. No pull request to
evilo, registration with the bundled source, or particular filename is required.

1. Create a catalog using the compact example below. The root only requires
   `name` and `plugins`; `{"name":"My plugins","plugins":[]}` is a valid empty source.
2. Publish the JSON on your static site, object storage, or HTTP(S) endpoint.
   The URL must return the JSON directly, not a repository page or a redirect.
   `plugin.json`, `plugins.json`, any other name, and URLs without `.json` work.
3. In evilo, open Browse's Plugin Sources action, choose Add plugin source, paste
   that URL, and subscribe. Open its source catalog, choose a plugin's details,
   review a compatible release's permissions, and install. Subscription alone
   does not create installed Browse entries or execute the listed plugins.

The independent template's `:evilo-plugin:exportPluginRelease` task already exports
a directly usable `plugins.json`, signed manifest, artifacts, and actual source
archive/hash. You may publish that generated JSON unchanged. For a curated source,
copy its release metadata into `availableVersions` and keep common fields once
per plugin. Use the template README for export inputs and commands; hashes and
source revisions must come from the actual export, not invented values.

Independent catalog hosting does **not** mean independent package signing. The
current app only accepts packages signed by its embedded application authority.
A custom source cannot add trusted keys or accept another publisher's signature.
This restriction is separate from catalog format and permission consent.

There is no separate JSON Schema file to maintain and no `$schema` property to
add. `CatalogParser` normalizes compact input and then validates the existing
typed catalog, URL policies, permissions, and release relationships.

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
      "permissions": {
        "api": {"origins": ["https://api.example.com"]},
        "media": {"origins": ["https://images.example.com"]}
      },
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

Each release inherits omitted `kinds`, `languages`, `sourceUrl`, `permissions`,
and `protocol` from its plugin. An explicit release field replaces the entire
common field, without merging objects or lists. For example, a release-specific
`permissions` object that omits `media` gets no media origins, even if the plugin
declares them. Missing members use SDK defaults, not common-field members.

## Update a source

Keep the subscription URL stable and append a release to `availableVersions`
with a new version and higher `versionCode`. Copy its manifest URL, actual source
revision/archive/hash, and publication date from export. The plugin's current
version and creation/update dates, and the source's update date, are derived
unless explicitly supplied. Users receive the new listing when the app refreshes.

Do not reuse version codes or silently replace old release metadata. Changing
common `permissions`, `protocol`, `kinds`, `languages`, or `sourceUrl` also changes
older releases that inherit them. When a new release differs, retain explicit
overrides for older releases or override the new release instead. Approved
installations keep their recorded permissions; changed normalized release metadata
blocks opening and requires review, and signed descriptor checks still apply.

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
catalogs share format version 1. The whole UTF-8 document must fit within 2 MiB.

## Plugin fields

| Field | Meaning |
| --- | --- |
| `id`, `name`, `authors` | Required stable signed plugin ID, display name, and 1 to 16 author names/objects. |
| `description` | Required introduction, at most 8192 characters. |
| `languages` | Required 1 to 32 distinct language tags such as `zh`, `zh-Hans`, or `en`; releases inherit it. |
| `kinds` | Required nonempty subset of `manga`, `novel`, `anime`, and `film`; releases inherit it. |
| `sourceUrl` | Required source repository URL, not a `.wlm`, `.wasm`, `.pwasm`, or `.cwasm` download. |
| `availableVersions` | Required 1 to 64 releases, with distinct version strings and version codes. |
| `permissions`, `protocol` | Optional common release declarations. Without either-level declarations, SDK defaults allow no network origins or storage and require protocol 1.0 with `media.catalog.v1`. |
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
installed copy and does not silently replace its data or permissions.

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
| `permissions` | Inherits plugin permissions; otherwise no network origins or storage. Explicit objects replace the common object. |

Release identity, kinds, languages, protocol requirements, and permissions must
match `evilo.descriptor` in the signed manifest and the guest's runtime descriptor.
`evilo.sourceRevision` and `evilo.sourceArchiveSha256` in the signed manifest must
match the catalog. The descriptor fits Wasmline's default 16 KiB metadata-string
limit. Catalog descriptions, dates, and link destinations are not
signed claims. The host does not download and inspect source archives or perform
a reproducible-build comparison; the signing operator must review the actual
archive and ensure the linked revision contains the released source. The template
exports actual source files and signs their archive hash, rather than exporting
a binary-only ZIP as source.

## Permissions and compatibility

Each `permissions.api` and `permissions.media` object has `origins`, `pathPrefixes`,
`methods`, and lowercase `headers`. Only the needed origins must be listed for
ordinary GET access: paths default to `["/"]`, methods to `["GET"]`, and headers to
`["accept", "user-agent"]`. Storage defaults to `false`. Narrow those defaults or
declare other allowed API methods when the signed plugin needs them.
Origins are exact HTTP(S) origins with no path,
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
are persisted and cannot be lowered by a later catalog refresh. Every source's
floor is scoped to that subscription, including the built-in source; it cannot
block another source's copy of the same signed plugin ID. Disabling a source
retains its floors. The built-in subscription can be disabled, not removed.
Removing a custom source removes its scoped floor, cache, and installations;
plugin data is retained. Install/update still requires explicit permission review.
An incompatible mandatory release requires updating the application, not
installing an older plugin.

## Browse identity and source copies

Source catalogs are the installation entry point and retain All/Installed filters.
Browse shows enabled sources' installed releases only and checks their actual
release kinds, not capabilities introduced by a newer catalog release. Withdrawn,
changed, or mandatory-update installations keep a management entry with their
blocking reason; disabling a source hides its installations without deleting them.

Duplicate display merging defaults on and uses verified installed `plugin.id`
within the app's one signing authority. A matching name, icon, or catalog-only ID
does not create a verified installation. Original `sourceId:pluginId` records,
permissions, version floors, timestamps, and per-source actions remain separate.
The default source is a saved installation key or the oldest installation with
subscription order as a tie-breaker. An unavailable default requires explicit
selection; neither search, source filtering, higher mirror versions, nor load
failure silently selects a replacement. Disabling/removing an implicit duplicate
default, or updating its installed release, pins its key before the mutation;
removing it preserves that choice while
another installation exists, until the user explicitly selects another source.

Same-ID/same-code cross-source differences in release identity, kinds/languages,
protocol, permissions, source revision, or source archive hash are shown as
declaration conflicts. Different mirror download URLs alone are not conflicts.
The detail page distinguishes subscription URLs from actual source-code links,
shows approved installed permissions separately from requested target permissions,
and uninstalls one named source copy. Shared plugin KV is not a promise of version
data compatibility or login synchronization. Source/installation behavior is
recorded in [the progress ledger](browse-plugin-plan-progress.md); live behavior
and test results for this redesign are not yet established.

HTTPS is preferred. Plain HTTP needs explicit source consent; signatures remain
mandatory. Package artifacts must stay in the manifest's origin and standard
`artifacts/sha256/<prefix>/<hash>.<format>` directory. Host-controlled HTTP does
not bypass browser CORS or HTTPS-page mixed-content restrictions. Source-code
links and playback opened in an external application use that application's networking rules,
not evilo's download policy.

## Bundled source and existing catalogs

`plugin-repository/plugin.json` is the default editable catalog. The app generates
`BundledPluginCatalogJson` from that one file. Its configured refresh URL is
`https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/plugin.json`;
this is configuration, not a verified published release. The bundled source stays
empty until actual signed packages and matching source archives are published.

Renaming the bundled file requires updating `catalogFile` in
`evilo-app/build.gradle.kts` and `DEFAULT_BUNDLED_CATALOG_URL` in the core's
`PluginCatalog.kt`. Independent sources do not require an app code change.

Full existing catalogs remain accepted. Explicit fields are preserved, including
full timestamp representations; only ordinary dates are expanded to UTC midnight.
Legacy `$schema` and removed review metadata are ignored. Parsing expands compact
input into the same full typed metadata stored in caches and installation records;
refreshing unchanged input does not invent new dates or change release permissions.
