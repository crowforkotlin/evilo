package crow.evilo.plugin.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okio.ByteString.Companion.decodeHex

/**
 * Checks the published index shape against the consumer. The fixture is the exact byte
 * sequence the builder signs, so a format or signing change fails here rather than later.
 */
class SubscriptionArtifactsTest {
    private val pointer = assertIs<SubscriptionContent.Pointer>(
        SubscriptionParser.classify(SubscriptionFixture.pointer("https://example.com/index.json"), false)).document

    @Test
    fun theIndexOfTheBuilderVerifies() {
        val index = SubscriptionParser.verifyIndex(
            pointer,
            SubscriptionFixture.INDEX_BODY.encodeToByteArray(),
            SubscriptionFixture.SIGNATURE_HEX.decodeHex().toByteArray(),
            SubscriptionParser.publicKey(SubscriptionFixture.PUBLIC_KEY_HEX),
            allowHttp = false,
        )
        assertEquals(1, index.schemaVersion)
        val entry = index.plugins.single()
        assertEquals("crow.evilo.source.copymanga", entry.plugin.id)
        assertEquals("CopyManga", entry.plugin.name)
        assertEquals(1, entry.plugin.availableVersions.size, "an indexed plugin carries its newest release")
        val release = entry.plugin.availableVersions.single()
        assertEquals("1.0.0", release.version)
        assertEquals("fde0c782bb879adab9301254aefb4e283d5631aec9f1b8e05679c73f4158d7e8", release.manifestSha256)
        assertEquals("manga", entry.plugin.kinds.single().name.lowercase())
    }

    @Test
    fun anInstalledReleaseCanBeChosenFromAnIndexEntry() {
        val index = SubscriptionParser.verifyIndex(
            pointer,
            SubscriptionFixture.INDEX_BODY.encodeToByteArray(),
            SubscriptionFixture.SIGNATURE_HEX.decodeHex().toByteArray(),
            SubscriptionParser.publicKey(SubscriptionFixture.PUBLIC_KEY_HEX),
            allowHttp = false,
        )
        // The list view and the install path both read availableVersions, so an entry with
        // no release would show a plugin the user cannot install.
        val catalog = indexAsCatalog(index.plugins, index.name, index.updatedAt)
        assertEquals(1, catalog.plugins.single().latestRelease.versionCode)
    }

    @Test
    fun aTamperedIndexIsRejected() {
        val tampered = SubscriptionFixture.INDEX_BODY.replace("CopyManga", "MalloryManga").encodeToByteArray()
        val failure = assertFailsWith<IllegalArgumentException> {
            SubscriptionParser.verifyIndex(pointer, tampered,
                SubscriptionFixture.SIGNATURE_HEX.decodeHex().toByteArray(),
                SubscriptionParser.publicKey(SubscriptionFixture.PUBLIC_KEY_HEX), false)
        }
        assertTrue(failure.message.orEmpty().contains("signature"))
    }

    @Test
    fun aSubstitutedKeyIsRejected() {
        val other = SubscriptionParser.publicKey("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val failure = assertFailsWith<IllegalArgumentException> {
            SubscriptionParser.verifyIndex(pointer, SubscriptionFixture.INDEX_BODY.encodeToByteArray(),
                SubscriptionFixture.SIGNATURE_HEX.decodeHex().toByteArray(), other, false)
        }
        assertTrue(failure.message.orEmpty().contains("fingerprint"))
    }
}
