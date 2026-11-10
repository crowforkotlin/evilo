package crow.evilo.storage

import okio.FileSystem
import okio.Path

class FileSnapshotPersistence(
    private val fileSystem: FileSystem,
    private val path: Path,
) : SnapshotPersistence {
    override fun read(): String? {
        if (!fileSystem.exists(path)) return null
        require((fileSystem.metadata(path).size ?: 0) <= 32L * 1024 * 1024) { "Application storage file is too large." }
        return fileSystem.read(path) { readUtf8() }
    }

    override fun write(snapshot: String) {
        val parent = requireNotNull(path.parent) { "Application storage requires a parent directory." }
        fileSystem.createDirectories(parent)
        val temporary = parent / "${path.name}.tmp"
        fileSystem.write(temporary) { writeUtf8(snapshot) }
        fileSystem.atomicMove(temporary, path)
    }
}
