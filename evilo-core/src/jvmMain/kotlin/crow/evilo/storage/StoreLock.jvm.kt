package crow.evilo.storage

actual class StoreLock actual constructor() {
    private val monitor = Any()
    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
