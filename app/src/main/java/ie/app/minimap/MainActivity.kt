package ie.app.minimap

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.ar.core.ArCoreApk
import dagger.hilt.android.AndroidEntryPoint
import ie.app.minimap.ui.theme.MiniMapTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            var installRequested = false

            LaunchedEffect(Unit) {
                val hasCameraPermission = checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCameraPermission) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            LaunchedEffect(Unit) {
                when (ArCoreApk.getInstance().requestInstall(this@MainActivity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Left empty; nothing needs to be done.
                    }
                }
            }
            MiniMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MiniMapNav(modifier = Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }
}
