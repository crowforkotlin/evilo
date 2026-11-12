@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package crow.evilo.storage

import androidx.datastore.core.CorruptionException
import crow.evilo.model.MediaKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class PluginValues(
    @ProtoNumber(1) val revision: Long = 0,
    @ProtoNumber(2) val entries: Map<String, String> = emptyMap(),
)

@Serializable
data class EviloPreferences(
    @ProtoNumber(1) val schemaVersion: Int = 1,
    @ProtoNumber(2) val selectedKind: MediaKind = MediaKind.MANGA,
    @ProtoNumber(3) val showNsfw: Boolean = false,
    @ProtoNumber(4) val pluginValues: Map<String, PluginValues> = emptyMap(),
    @ProtoNumber(5) val mergeDuplicatePlugins: Boolean = true,
    @ProtoNumber(6) val preferredInstallations: Map<String, String> = emptyMap(),
)

data class EviloSettings(
    val selectedKind: MediaKind = MediaKind.MANGA,
    val showNsfw: Boolean = false,
    val mergeDuplicatePlugins: Boolean = true,
    val preferredInstallations: Map<String, String> = emptyMap(),
)

object EviloPreferencesCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val protobuf = ProtoBuf { encodeDefaults = true }

    fun encode(value: EviloPreferences): ByteArray {
        require(value.schemaVersion == 1) { "Unsupported preferences schema ${value.schemaVersion}." }
        require(value.pluginValues.values.all { it.revision >= 0 }) { "Invalid plugin storage revision." }
        require(validPreferredInstallations(value.preferredInstallations)) { "Invalid preferred plugin installations." }
        return protobuf.encodeToByteArray(EviloPreferences.serializer(), value).also {
            require(it.size <= MAX_BYTES) { "Application preferences exceed their byte limit." }
        }
    }

    fun decode(bytes: ByteArray): EviloPreferences {
        if (bytes.size > MAX_BYTES) throw CorruptionException("Application preferences exceed their byte limit.")
        try {
            return protobuf.decodeFromByteArray(EviloPreferences.serializer(), bytes).also {
                if (it.schemaVersion != 1 || it.pluginValues.values.any { values -> values.revision < 0 } ||
                    !validPreferredInstallations(it.preferredInstallations)) {
                    throw CorruptionException("Unsupported or invalid application preferences schema.")
                }
            }
        } catch (failure: SerializationException) {
            throw CorruptionException("Cannot decode application preferences.", failure)
        }
    }
}

internal fun validPreferredInstallations(values: Map<String, String>): Boolean = values.size <= 256 && values.all { (id, key) ->
    val sourceId = key.substringBefore(':')
    id.length <= 128 && PREFERRED_PLUGIN_ID.matches(id) && key.length <= 257 && sourceId.length in 1..128 &&
        PREFERRED_SOURCE_ID.matches(sourceId) &&
        key.substringAfter(':', "") == id
}

private val PREFERRED_PLUGIN_ID = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")
private val PREFERRED_SOURCE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
