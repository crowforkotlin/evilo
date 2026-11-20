package crow.evilo.app

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private val appReady = mutableStateOf(false)
    private var localNetworkPermissionHandled = false
    private var localNetworkPermissionPending = false
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkPermissionPending = false
        localNetworkPermissionHandled = true
        appReady.value = true
        if (!granted) Toast.makeText(this, R.string.local_network_permission_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val darkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            navigationBarStyle = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                // Android 7 cannot draw dark navigation icons, so retain contrast there.
                else SystemBarStyle.light(Color.TRANSPARENT, Color.rgb(0x1b, 0x1b, 0x1b)),
        )
        super.onCreate(savedInstanceState)
        localNetworkPermissionHandled = savedInstanceState?.getBoolean(PERMISSION_HANDLED) ?: false
        localNetworkPermissionPending = savedInstanceState?.getBoolean(PERMISSION_PENDING) ?: false
        // Resolve permission before App initializes and refreshes installed LAN sources.
        val needsPermission = Build.VERSION.SDK_INT >= 37 && applicationInfo.targetSdkVersion >= 37 &&
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        appReady.value = !needsPermission || localNetworkPermissionHandled

        setContent {
            if (appReady.value) App()
        }
        if (!appReady.value && !localNetworkPermissionPending) {
            localNetworkPermissionPending = true
            localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PERMISSION_HANDLED, localNetworkPermissionHandled)
        outState.putBoolean(PERMISSION_PENDING, localNetworkPermissionPending)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        const val PERMISSION_HANDLED = "localNetworkPermissionHandled"
        const val PERMISSION_PENDING = "localNetworkPermissionPending"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
