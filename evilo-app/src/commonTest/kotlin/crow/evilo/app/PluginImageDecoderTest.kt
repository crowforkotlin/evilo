package crow.evilo.app

import crow.evilo.feature.reader.PluginImageFormatException
import crow.evilo.feature.reader.PluginImageLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PluginImageDecoderTest {
    @Test
    fun platformDecodeFailuresHaveAFormatCategoryWithoutMatchingExceptionMessages() = runTest {
        val original = IllegalArgumentException("Invalid pixel data.")
        val failure = assertFailsWith<PluginImageFormatException> { decodePluginImage { throw original } }
        assertSame(original, failure.cause)
    }

    @Test
    fun decodingDoesNotConvertCancellationOrLimitFailuresIntoFormatErrors() = runTest {
        val cancellation = CancellationException("Leaving the reader.")
        assertSame(cancellation, assertFailsWith<CancellationException> { decodePluginImage { throw cancellation } })
        val limit = PluginImageLimitException(maxDimension = 512, maxPixels = 262_144)
        assertSame(limit, assertFailsWith<PluginImageLimitException> { decodePluginImage { throw limit } })
    }

    @Test
    fun readsPngDimensionsWithoutDecodingPixels() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
            0, 0, 2, 0, 0, 0, 3, 0,
        )
        assertEquals(512 to 768, pluginImageDimensions(header))
    }

    @Test
    fun readsJpegFrameDimensions() {
        val header = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xc0.toByte(),
            0, 8, 8, 3, 0, 2, 0, 1)
        assertEquals(512 to 768, pluginImageDimensions(header))
    }

    @Test
    fun readsExtendedWebpDimensions() {
        val header = webpHeader("VP8X")
        header[24] = 0x7f
        header[25] = 0x02
        header[27] = 0xff.toByte()
        header[28] = 0x04
        assertEquals(640 to 1280, pluginImageDimensions(header))
    }

    @Test
    fun readsLosslessWebpDimensions() {
        val header = webpHeader("VP8L")
        header[20] = 0x2f
        header[21] = 0xff.toByte()
        header[22] = 0xc1.toByte()
        header[23] = 0xbf.toByte()
        assertEquals(512 to 768, pluginImageDimensions(header))
    }

    @Test
    fun oversizedPngIsRejectedBeforePixelDecoding() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
            0, 1, 0, 0, 0, 1, 0, 0,
        )
        assertFailsWith<IllegalStateException> { validatePluginImage(header) }
    }

    @Test
    fun unknownOrTruncatedImageHeadersAreRejected() {
        assertFailsWith<IllegalStateException> { pluginImageDimensions("not an image".encodeToByteArray()) }
        assertFailsWith<IllegalStateException> { pluginImageDimensions(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xc0.toByte(), 0, 8)) }
    }

    @Test
    fun unsupportedFormatsAndInvalidDimensionsHaveFormatFailures() {
        for (header in listOf("GIF89a", "<svg></svg>", "<html>Denied</html>", "AVIF"))
            assertFailsWith<PluginImageFormatException> { pluginImageDimensions(header.encodeToByteArray()) }
        assertFailsWith<PluginImageFormatException> { requirePluginDimensions(0, 512, 512, 262_144) }
        assertFailsWith<PluginImageFormatException> { requirePluginDimensions(-1, 512, 512, 262_144) }
    }

    @Test
    fun iconAndMediaLimitsRemainSeparateAndCheckTotalPixels() {
        requirePluginDimensions(512, 512, 512, 262_144)
        val icon = assertFailsWith<PluginImageLimitException> { requirePluginDimensions(513, 512, 512, 262_144) }
        assertEquals(512, icon.maxDimension)
        assertEquals(262_144L, icon.maxPixels)
        val media = assertFailsWith<PluginImageLimitException> { requirePluginDimensions(8192, 8192, 16_384, 33_554_432) }
        assertEquals(16_384, media.maxDimension)
        assertEquals(33_554_432L, media.maxPixels)
    }

    private fun webpHeader(chunk: String): ByteArray = ByteArray(30).also { header ->
        "RIFF".encodeToByteArray().copyInto(header)
        "WEBP".encodeToByteArray().copyInto(header, 8)
        chunk.encodeToByteArray().copyInto(header, 12)
    }
}
