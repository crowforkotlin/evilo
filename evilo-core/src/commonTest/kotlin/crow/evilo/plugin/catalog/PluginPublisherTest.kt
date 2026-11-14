package crow.evilo.plugin.catalog

import crow.wasmline.loader.WasmlineTrustedKeys
import okio.ByteString.Companion.decodeHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginPublisherTest {
    private val officialHex = "01".repeat(32)
    private val publisherHex = "02".repeat(32)
    private val official = WasmlineTrustedKeys { algorithm, keyId ->
        officialHex.decodeHex().toByteArray().takeIf { algorithm == "Ed25519" && keyId == OFFICIAL_PLUGIN_KEY_ID }
    }

    @Test
    fun thirdPartyLookupCannotAuthorizeTheOfficialKeyOrAnotherKeyId() {
        val release = catalogRelease().copy(signing = PluginSigningIdentity("publisher-test", publisherHex), manifestSha256 = "a".repeat(64))
        val keys = releaseVerificationKeys(release, official)
        assertEquals(publisherHex, requireNotNull(keys.getPublicKey("Ed25519", "publisher-test")).joinToString("") { it.toUByte().toString(16).padStart(2, '0') })
        assertNull(keys.getPublicKey("Ed25519", OFFICIAL_PLUGIN_KEY_ID))
        assertNull(keys.getPublicKey("Ed25519", null))
        assertNull(keys.getPublicKey("Other", "publisher-test"))
        assertFalse(isOfficialRelease(release, official))
    }

    @Test
    fun publisherStorageCannotCollideWithAnOfficialPluginId() {
        val officialRelease = catalogRelease()
        val thirdParty = officialRelease.copy(signing = PluginSigningIdentity("publisher-test", publisherHex), manifestSha256 = "a".repeat(64))
        val key = publisherStorageKey("test.plugin", thirdParty, official)
        assertTrue(key.startsWith("publisher:"))
        assertEquals("test.plugin", publisherStorageKey("test.plugin", officialRelease, official))
        assertEquals(key, publisherStorageKey("test.plugin", thirdParty.copy(versionCode = 2), official))
        assertFalse(key == publisherStorageKey("test.plugin", thirdParty.copy(signing = PluginSigningIdentity("other", "03".repeat(32))), official))
    }
}
