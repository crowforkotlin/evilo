package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import crow.evilo.storage.SnapshotPersistence
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString

private external interface BrowserStorage : JsAny {
    fun getItem(key: JsString): JsString?
    fun setItem(key: JsString, value: JsString)
}

private external val localStorage: BrowserStorage

@Composable
internal actual fun rememberAppPersistence(): SnapshotPersistence = remember {
    object : SnapshotPersistence {
        override fun read(): String? = localStorage.getItem("evilo.state.v1".toJsString())?.toString()
        override fun write(snapshot: String) { localStorage.setItem("evilo.state.v1".toJsString(), snapshot.toJsString()) }
    }
}
