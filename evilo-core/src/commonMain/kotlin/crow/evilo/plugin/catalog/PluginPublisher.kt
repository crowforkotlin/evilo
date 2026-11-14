package crow.evilo.plugin.catalog

import crow.wasmline.loader.WasmlineTrustedKeys
import okio.ByteString.Companion.decodeHex

const val OFFICIAL_PLUGIN_KEY_ID = "evilo-root-v1"

fun PluginSigningIdentity.fingerprint(): String = publicKeyHex.decodeHex().sha256().hex()

/** Declared keys are confined to one release, never added to an application-wide trust list. */
fun releaseVerificationKeys(release: PluginRelease, officialKeys: WasmlineTrustedKeys): WasmlineTrustedKeys {
    val signing = release.signing ?: return officialKeys
    require(signing.algorithm == "Ed25519" && release.manifestSha256 != null) { "Publisher releases require signed, pinned manifests." }
    val bytes = signing.publicKeyHex.decodeHex().toByteArray()
    require(bytes.size == 32) { "Invalid publisher public key." }
    return WasmlineTrustedKeys { algorithm, keyId ->
        bytes.copyOf().takeIf { algorithm == signing.algorithm && keyId == signing.keyId }
    }
}

fun isOfficialRelease(release: PluginRelease, officialKeys: WasmlineTrustedKeys): Boolean {
    val signing = release.signing ?: return true // Legacy releases still must pass the official signature check.
    val official = officialKeys.getPublicKey("Ed25519", OFFICIAL_PLUGIN_KEY_ID) ?: return false
    return official.contentEquals(signing.publicKeyHex.decodeHex().toByteArray())
}

/** Preserve existing official storage; third-party publishers cannot claim its namespace by ID. */
fun publisherStorageKey(pluginId: String, release: PluginRelease, officialKeys: WasmlineTrustedKeys): String =
    if (isOfficialRelease(release, officialKeys)) pluginId
    else "publisher:${requireNotNull(release.signing).fingerprint()}:$pluginId"
