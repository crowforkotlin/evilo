package crow.evilo.app

internal fun validatePluginImage(bytes: ByteArray, maxDimension: Int = 16_384, maxPixels: Long = 32L * 1024 * 1024) {
    // Inspect dimensions before allocating pixels; a small compressed file can describe an enormous image.
    val (width, height) = pluginImageDimensions(bytes)
    requirePluginDimensions(width, height, maxDimension, maxPixels)
}

internal fun pluginImageDimensions(bytes: ByteArray): Pair<Int, Int> {
    if (bytes.size >= 24 && bytes.u8(0) == 0x89 && bytes.matches(1, "PNG\r\n\u001a\n") && bytes.matches(12, "IHDR")) {
        return bytes.int32be(16) to bytes.int32be(20)
    }
    if (bytes.size >= 30 && bytes.matches(0, "RIFF") && bytes.matches(8, "WEBP")) {
        return when {
            bytes.matches(12, "VP8X") -> (bytes.int24le(24) + 1) to (bytes.int24le(27) + 1)
            bytes.matches(12, "VP8L") && bytes.u8(20) == 0x2f -> {
                val width = (bytes.u8(21) or (bytes.u8(22) shl 8)) and 0x3fff
                val height = (bytes.u8(22) shr 6) or (bytes.u8(23) shl 2) or ((bytes.u8(24) and 0x0f) shl 10)
                (width + 1) to (height + 1)
            }
            bytes.matches(12, "VP8 ") && bytes.u8(23) == 0x9d && bytes.u8(24) == 0x01 && bytes.u8(25) == 0x2a ->
                ((bytes.u8(26) or (bytes.u8(27) shl 8)) and 0x3fff) to
                    ((bytes.u8(28) or (bytes.u8(29) shl 8)) and 0x3fff)
            else -> error("Unsupported or malformed WebP image header.")
        }
    }
    if (bytes.size >= 4 && bytes.u8(0) == 0xff && bytes.u8(1) == 0xd8) {
        var offset = 2
        while (offset + 3 < bytes.size) {
            check(bytes.u8(offset) == 0xff) { "Malformed JPEG image header." }
            while (offset < bytes.size && bytes.u8(offset) == 0xff) offset++
            if (offset >= bytes.size) break
            val marker = bytes.u8(offset++)
            if (marker == 0xda || marker == 0xd9) break
            if (marker == 0x01 || marker in 0xd0..0xd8) continue
            check(offset + 1 < bytes.size) { "Truncated JPEG image header." }
            val length = bytes.int16be(offset)
            check(length >= 2 && offset + length <= bytes.size) { "Truncated JPEG image header." }
            if (marker in 0xc0..0xcf && marker !in listOf(0xc4, 0xc8, 0xcc)) {
                check(length >= 7) { "Truncated JPEG image dimensions." }
                return bytes.int16be(offset + 5) to bytes.int16be(offset + 3)
            }
            offset += length
        }
        error("JPEG image contains no supported size header.")
    }
    error("The reader supports JPEG, PNG, and WebP images only.")
}

internal fun requirePluginDimensions(width: Int, height: Int, maxDimension: Int, maxPixels: Long) {
    check(width in 1..maxDimension && height in 1..maxDimension && width.toLong() * height <= maxPixels) {
        "Image exceeds its limit: $maxDimension pixels per dimension and $maxPixels total pixels."
    }
}

private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xff

private fun ByteArray.int16be(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

private fun ByteArray.int24le(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16)

private fun ByteArray.int32be(offset: Int): Int = (u8(offset) shl 24) or (u8(offset + 1) shl 16) or
    (u8(offset + 2) shl 8) or u8(offset + 3)

private fun ByteArray.matches(offset: Int, value: String): Boolean = offset + value.length <= size &&
    value.indices.all { u8(offset + it) == value[it].code }
