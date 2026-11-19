package crow.evilo.plugin.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubscriptionParserTest {
    @Test
    fun aPluginCatalogIsRecognised() {
        val content = SubscriptionParser.classify("""{"name":"P","plugins":[]}""", false)
        assertIs<SubscriptionContent.PluginCatalog>(content)
    }

    @Test
    fun aPointerCarriesTheIndexAddressAndFingerprint() {
        val content = SubscriptionParser.classify("""
            {"name":"A","indexUrl":"https://a.example.com/index.json",
             "signingKeyFingerprint":"${"a".repeat(64)}"}
        """.trimIndent(), false)
        val pointer = assertIs<SubscriptionContent.Pointer>(content)
        assertEquals("https://a.example.com/index.json", pointer.document.indexUrl)
    }

    @Test
    fun aDocumentWithTwoShapesIsRejected() {
        val failure = assertFailsWith<IllegalArgumentException> {
            SubscriptionParser.classify("""{"plugins":[],"indexUrl":"https://a.example.com/i.json"}""", false)
        }
        assertTrue(failure.message.orEmpty().contains("exactly one"))
    }

    @Test
    fun aDocumentWithoutARecognisedShapeIsRejected() {
        assertFailsWith<IllegalArgumentException> { SubscriptionParser.classify("""{"name":"x"}""", false) }
    }

    @Test
    fun aMalformedFingerprintIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SubscriptionParser.classify("""{"name":"A","indexUrl":"https://a.example.com/i.json",
                "signingKeyFingerprint":"abc"}""", false)
        }
    }
}
