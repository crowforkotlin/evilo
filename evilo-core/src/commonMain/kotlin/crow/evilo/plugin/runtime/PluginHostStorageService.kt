package crow.evilo.plugin.runtime

import crow.evilo.plugin.api.EviloHostService
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.storage.KeyValueStore

class PluginHostStorageService(
    pluginId: String,
    private val host: HostDescriptor,
    private val store: KeyValueStore,
    private val permitted: Boolean,
) : EviloHostService {
    private val namespace = "plugin.$pluginId"

    override fun describeHost(): String = PluginJson.encodeToString(host)

    override fun readValue(key: String): String? {
        approveKey(key)
        return store.read(namespace, key)
    }

    override fun writeValue(key: String, value: String): Boolean {
        approveKey(key)
        require(value.encodeToByteArray().size <= host.storageValueLimitBytes) { "Plugin storage value exceeds its byte limit." }
        store.updateNamespace(namespace) { current ->
            val next = current + (key to value)
            require(next.size <= host.storageEntryLimit) { "Plugin storage entry limit exceeded." }
            require(next.entries.sumOf { it.key.encodeToByteArray().size + it.value.encodeToByteArray().size } <= host.storageQuotaBytes) {
                "Plugin storage quota exceeded."
            }
            next
        }
        return true
    }

    override fun removeValue(key: String): Boolean {
        approveKey(key)
        return store.remove(namespace, key)
    }

    override fun listKeys(prefix: String): String {
        check(permitted) { "Plugin storage permission was not granted." }
        require(prefix.length <= 128 && prefix.none { it < ' ' || it == '\u007f' }) { "Invalid storage key prefix." }
        return PluginJson.encodeToString(store.keys(namespace, prefix).take(host.storageEntryLimit))
    }

    private fun approveKey(key: String) {
        check(permitted) { "Plugin storage permission was not granted." }
        require(key.length in 1..128 && key.none { it < ' ' || it == '\u007f' }) { "Invalid plugin storage key." }
    }
}
