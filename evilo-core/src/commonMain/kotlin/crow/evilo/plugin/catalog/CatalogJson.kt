package crow.evilo.plugin.catalog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

internal fun normalizeCatalogJson(catalog: JsonObject): JsonObject {
    val entries = requireNotNull(catalog["plugins"]) { "A plugin source must declare its plugins list." }.jsonArray
    require(entries.size <= 512) { "A catalog may contain at most 512 plugins." }
    val plugins = entries.map { normalizePluginJson(it.jsonObject) }
    val updatedAt = plugins.map { it.getValue("updatedAt") }.maxByOrNull { timestamp(it) }
        ?: JsonPrimitive("1970-01-01T00:00:00Z")
    return normalizeDates(JsonObject(catalog + ("plugins" to JsonArray(plugins))), "updatedAt").withDefaults(
        "schemaVersion" to JsonPrimitive(PLUGIN_CATALOG_SCHEMA_VERSION),
        "id" to JsonPrimitive("catalog"),
        "description" to JsonPrimitive(""),
        "updatedAt" to updatedAt,
    )
}

private fun normalizePluginJson(plugin: JsonObject): JsonObject {
    val versions = requireNotNull(plugin["availableVersions"]) { "A plugin must declare its availableVersions list." }.jsonArray
    require(versions.size in 1..64) { "A plugin must list 1 to 64 available versions." }
    // Only omitted fields inherit defaults; an explicit release override is never merged or replaced.
    val inherited = listOf("kinds", "languages", "sourceUrl", "permissions", "protocol")
        .mapNotNull { key -> plugin[key]?.let { key to it } }.toTypedArray()
    val releases = versions.map { normalizeDates(it.jsonObject.withDefaults(*inherited), "publishedAt") }
    val latest = releases.maxBy { release ->
        requireNotNull(release["versionCode"]?.jsonPrimitive?.longOrNull) { "A release must declare its integer versionCode." }
    }
    val dates = releases.map { requireNotNull(it["publishedAt"]) { "A release must declare its publishedAt date." } }
    val fields = plugin.toMutableMap()
    fields["availableVersions"] = JsonArray(releases)
    plugin["authors"]?.let { authors ->
        fields["authors"] = JsonArray(authors.jsonArray.map { author ->
            if (author is JsonPrimitive && author.isString) JsonObject(mapOf("name" to author)) else author
        })
    }
    return normalizeDates(JsonObject(fields), "createdAt", "updatedAt").withDefaults(
        "overseas" to JsonPrimitive(false),
        "nsfw" to JsonPrimitive(false),
        "iconUrl" to JsonPrimitive(""),
        "version" to requireNotNull(latest["version"]) { "A release must declare its version." },
        "createdAt" to dates.minBy { timestamp(it) },
        "updatedAt" to dates.maxBy { timestamp(it) },
    )
}

private fun JsonObject.withDefaults(vararg defaults: Pair<String, JsonElement>): JsonObject = JsonObject(toMutableMap().apply {
    defaults.forEach { (key, value) -> getOrPut(key) { value } }
})

private fun normalizeDates(value: JsonObject, vararg fields: String): JsonObject = JsonObject(value.mapValues { (key, date) ->
    if (key in fields && date is JsonPrimitive && date.isString && DATE.matches(date.content))
        JsonPrimitive("${date.content}T00:00:00Z") else date
})

private fun timestamp(value: JsonElement): Instant = Instant.parse(value.jsonPrimitive.content)

private val DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
