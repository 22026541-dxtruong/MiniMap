package ie.app.minimap.ui.ar

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.sceneform.ArSceneView
import ie.app.minimap.data.local.entity.Node

@SuppressLint("ClickableViewAccessibility", "LocalContextResourcesRead")
@Composable
fun ArViewViewer(
    nodes: List<Node>,
    onMessage: (String) -> Unit,
    pathNode: List<Node>? = null,
    updateUserLocation: (Offset?) -> Unit = {},
    viewModel: ArViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val isLoading by remember { derivedStateOf { uiState.loading } }
    val errorMessage by remember { derivedStateOf { uiState.error } }
    val currentMessage by remember { derivedStateOf { uiState.message } }

    val arSceneView = remember {
        ArSceneView(context).apply {
            planeRenderer.isVisible = false // Tắt plane renderer trong chế độ viewer để mượt hơn
            planeRenderer.isEnabled = false
        }
    }

    LaunchedEffect(nodes) {
        viewModel.updateCloudAnchors(nodes)
    }

    LaunchedEffect(pathNode) {
        if (pathNode != null)
            viewModel.drawPath(arSceneView, pathNode)
    }

    LaunchedEffect(currentMessage) {
        currentMessage?.let {
            onMessage(currentMessage!!)
            viewModel.clearMessage()
        }
    }

    DisposableEffect(lifecycleOwner, arSceneView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume(arSceneView)
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause(arSceneView)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onDestroyView(arSceneView)
        }
    }

    DisposableEffect(arSceneView) {
        val updateListener = com.google.ar.sceneform.Scene.OnUpdateListener {
            if (!isLoading) {
                val frame = arSceneView.arFrame
                if (frame != null) {
                    val cameraPose = frame.camera.pose
                    val location = viewModel.updateUserLocationFromWorld(cameraPose)
                    if (location != null) {
                        updateUserLocation(location)
                    }
                }
                viewModel.onUpdate(arSceneView)
            }
        }

        arSceneView.scene.addOnUpdateListener(updateListener)
        onDispose {
            arSceneView.scene.removeOnUpdateListener(updateListener)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { arSceneView },
            modifier = Modifier.fillMaxSize()
        )

        when {
            isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.message ?: "Đang xử lý...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            errorMessage != null -> {
                Text(
                    text = "Lỗi: $errorMessage",
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }
}
