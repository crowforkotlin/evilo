package crow.evilo.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class PluginManifestCacheTest {
    @Test
    fun signedBytesRoundTripWithoutSharingMutableArrays() = runTest {
        val cache = MemoryPluginManifestCache()
        val original = "{\"signature\":\"signed\", \"manifest\":{}}\n".encodeToByteArray()
        val expected = original.copyOf()
        val key = original.digest()
        cache.writeVerified(key, original)
        original.fill(0)
        val firstRead = assertNotNull(cache.read(key))
        assertContentEquals(expected, firstRead)
        firstRead.fill(1)
        assertContentEquals(expected, cache.read(key))
    }

    @Test
    fun writesRejectNoncanonicalKeysAndBytesThatDoNotMatchTheirDigest() = runTest {
        val cache = MemoryPluginManifestCache()
        val bytes = "signed manifest".encodeToByteArray()
        val key = bytes.digest()
        for (invalid in listOf("", key.drop(1), key.uppercase(), "g".repeat(64), "plugin.one")) {
            assertFailsWith<IllegalArgumentException> { cache.writeVerified(invalid, bytes) }
        }
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(key, "different manifest".encodeToByteArray()) }
        assertNull(cache.read(key))
        cache.writeVerified(key, bytes)
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(key, byteArrayOf(1)) }
        assertContentEquals(bytes, cache.read(key))
    }

    @Test
    fun writeBoundsRejectEmptyAndOversizedBytesAndAcceptTheExactLimit() = runTest {
        val cache = MemoryPluginManifestCache(maxManifestBytes = 4, maxCacheBytes = 4)
        val empty = byteArrayOf()
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(empty.digest(), empty) }
        val oversized = ByteArray(5)
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(oversized.digest(), oversized) }
        val exact = ByteArray(4)
        cache.writeVerified(exact.digest(), exact)
        assertContentEquals(exact, cache.read(exact.digest()))
        val smallerCache = MemoryPluginManifestCache(maxManifestBytes = 8, maxCacheBytes = 3)
        assertFailsWith<IllegalArgumentException> { smallerCache.writeVerified(exact.digest(), exact) }
    }

    @Test
    fun readsTouchRecencyBeforeEntryCountEviction() = runTest {
        val cache = MemoryPluginManifestCache(maxEntries = 2)
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        val third = byteArrayOf(3)
        cache.writeVerified(first.digest(), first)
        cache.writeVerified(second.digest(), second)
        cache.read(first.digest())
        cache.writeVerified(third.digest(), third)
        assertNull(cache.read(second.digest()))
        assertContentEquals(first, cache.read(first.digest()))
        assertContentEquals(third, cache.read(third.digest()))
    }

    @Test
    fun byteEvictionAndRewritesCountEachDigestOnlyOnce() = runTest {
        val cache = MemoryPluginManifestCache(maxManifestBytes = 4, maxCacheBytes = 6)
        val first = byteArrayOf(1, 1, 1)
        val second = byteArrayOf(2, 2, 2)
        val third = byteArrayOf(3, 3, 3)
        cache.writeVerified(first.digest(), first)
        cache.writeVerified(second.digest(), second)
        cache.writeVerified(first.digest(), first)
        assertContentEquals(second, cache.read(second.digest()))
        cache.writeVerified(third.digest(), third)
        assertNull(cache.read(first.digest()))
        assertContentEquals(second, cache.read(second.digest()))
        assertContentEquals(third, cache.read(third.digest()))
    }

    @Test
    fun removingEntriesIsIdempotentAndFreesTheByteBudget() = runTest {
        val cache = MemoryPluginManifestCache(maxManifestBytes = 4, maxCacheBytes = 4)
        val first = byteArrayOf(1, 1)
        val second = byteArrayOf(2, 2)
        val third = byteArrayOf(3, 3)
        cache.writeVerified(first.digest(), first)
        cache.writeVerified(second.digest(), second)
        cache.remove(first.digest())
        cache.remove(first.digest())
        cache.remove("unknown")
        cache.writeVerified(third.digest(), third)
        assertNull(cache.read(first.digest()))
        assertContentEquals(second, cache.read(second.digest()))
        assertContentEquals(third, cache.read(third.digest()))
    }

    @Test
    fun configurableLimitsCannotDisableProductionBounds() {
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxManifestBytes = 0) }
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxCacheBytes = 0) }
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxEntries = 0) }
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxManifestBytes = PluginManifestCache.MAX_MANIFEST_BYTES + 1) }
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxCacheBytes = PluginManifestCache.MAX_CACHE_BYTES + 1) }
        assertFailsWith<IllegalArgumentException> { MemoryPluginManifestCache(maxEntries = PluginManifestCache.MAX_ENTRIES + 1) }
    }

    @Test
    fun concurrentWritesAndReadsKeepAllEntriesWithinTheBudget() = runTest {
        val cache = MemoryPluginManifestCache()
        val values = (0 until 64).map { "signed manifest $it".encodeToByteArray() }
        values.map { bytes ->
            async(Dispatchers.Default) {
                cache.writeVerified(bytes.digest(), bytes)
                assertContentEquals(bytes, cache.read(bytes.digest()))
            }
        }.awaitAll()
        for (bytes in values) assertContentEquals(bytes, cache.read(bytes.digest()))
    }

    @Test
    fun roomReadDeletesCorruptBytesAndMismatchedSizeColumns() = runTest {
        val cache = FakeRoomPluginManifestCache()
        val bytes = "signed manifest".encodeToByteArray()
        val key = bytes.digest()
        for (entry in listOf(
            PluginManifestCacheEntity(key, "corrupt manifest".encodeToByteArray(), 16, 1),
            PluginManifestCacheEntity(key, bytes, bytes.size + 1, 1),
            PluginManifestCacheEntity(key, byteArrayOf(), 0, 1),
            PluginManifestCacheEntity(key, ByteArray(PluginManifestCache.MAX_MANIFEST_BYTES + 1), PluginManifestCache.MAX_MANIFEST_BYTES + 1, 1),
        )) {
            cache.entries[key] = entry
            assertNull(cache.read(key))
            assertTrue(cache.entries.isEmpty())
        }
    }

    @Test
    fun roomReadCopiesBytesAndTouchesRecencyBeforeEviction() = runTest {
        val cache = FakeRoomPluginManifestCache()
        val oldest = "oldest signed manifest".encodeToByteArray()
        cache.writeVerified(oldest.digest(), oldest)
        val other = (1 until PluginManifestCache.MAX_ENTRIES).map { "signed manifest $it".encodeToByteArray() }
        for (bytes in other) cache.writeVerified(bytes.digest(), bytes)
        val beforeRead = Clock.System.now().toEpochMilliseconds()
        val returned = assertNotNull(cache.read(oldest.digest()))
        returned.fill(0)
        assertContentEquals(oldest, cache.entries.getValue(oldest.digest()).bytes)
        assertTrue(cache.entries.getValue(oldest.digest()).lastUsedEpochMillis >= beforeRead)
        val newest = "new signed manifest".encodeToByteArray()
        cache.writeVerified(newest.digest(), newest)
        assertEquals(PluginManifestCache.MAX_ENTRIES, cache.entries.size)
        assertNull(cache.read(other.first().digest()))
        assertContentEquals(oldest, cache.read(oldest.digest()))
    }

    @Test
    fun roomWritesEnforceTheByteBudgetAndCopyTheirInput() = runTest {
        val cache = FakeRoomPluginManifestCache()
        val values = (0..8).map { value -> ByteArray(PluginManifestCache.MAX_MANIFEST_BYTES) { value.toByte() } }
        for (bytes in values) cache.writeVerified(bytes.digest(), bytes)
        assertEquals(8, cache.entries.size)
        assertNull(cache.read(values.first().digest()))
        assertEquals(PluginManifestCache.MAX_CACHE_BYTES.toLong(), cache.entries.values.sumOf { it.bytes.size.toLong() })
        val last = values.last()
        val key = last.digest()
        val expected = last.copyOf()
        last.fill(42)
        assertContentEquals(expected, cache.read(key))
    }

    @Test
    fun roomInvalidWritesLeaveExistingEntriesUnchanged() = runTest {
        val cache = FakeRoomPluginManifestCache()
        val bytes = "signed manifest".encodeToByteArray()
        val key = bytes.digest()
        cache.writeVerified(key, bytes)
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(key, byteArrayOf(1)) }
        val oversized = ByteArray(PluginManifestCache.MAX_MANIFEST_BYTES + 1)
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(oversized.digest(), oversized) }
        assertFailsWith<IllegalArgumentException> { cache.writeVerified(key.uppercase(), bytes) }
        assertEquals(1, cache.entries.size)
        assertContentEquals(bytes, cache.read(key))
    }

    private fun ByteArray.digest(): String = toByteString().sha256().hex()

    // Exercises DAO policy without a generated database; it does not test Room transactions or SQL.
    private class FakeRoomPluginManifestCache : RoomPluginManifestCache() {
        val entries = mutableMapOf<String, PluginManifestCacheEntity>()

        override suspend fun find(manifestSha256: String): PluginManifestCacheEntity? = entries[manifestSha256]
        override suspend fun save(entity: PluginManifestCacheEntity) { entries[entity.manifestSha256] = entity }
        override suspend fun touch(manifestSha256: String, lastUsedEpochMillis: Long) {
            entries[manifestSha256]?.let { entries[manifestSha256] = it.copy(lastUsedEpochMillis = lastUsedEpochMillis) }
        }
        override suspend fun latestUse(): Long? = entries.values.maxOfOrNull { it.lastUsedEpochMillis }
        override suspend fun totalSizeBytes(): Long = entries.values.sumOf { it.bytes.size.toLong() }
        override suspend fun entryCount(): Int = entries.size
        override suspend fun evictOldest() {
            val oldest = entries.values.minWithOrNull(compareBy<PluginManifestCacheEntity> { it.lastUsedEpochMillis }.thenBy { it.manifestSha256 })
            if (oldest != null) entries.remove(oldest.manifestSha256)
        }
        override suspend fun remove(manifestSha256: String) { entries.remove(manifestSha256) }
    }
}
