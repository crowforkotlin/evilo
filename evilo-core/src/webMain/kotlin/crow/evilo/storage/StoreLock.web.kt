package crow.evilo.storage

actual class StoreLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}
