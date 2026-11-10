package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import crow.evilo.storage.SnapshotPersistence

private external interface BrowserStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

private external val localStorage: BrowserStorage

@Composable
internal actual fun rememberAppPersistence(): SnapshotPersistence = remember {
    object : SnapshotPersistence {
        override fun read(): String? = localStorage.getItem("evilo.state.v1")
        override fun write(snapshot: String) { localStorage.setItem("evilo.state.v1", snapshot) }
    }
}
