package ie.app.minimap.ui.ar

import android.annotation.SuppressLint
import android.util.Log
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

@SuppressLint("ClickableViewAccessibility")
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
    // Lấy LifecycleOwner
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val arSceneView = remember {
        ArSceneView(context).apply {
            planeRenderer.isVisible = true
            planeRenderer.isEnabled = true
        }
    }

    LaunchedEffect(nodes) {
        Log.d("ArViewModel", "${nodes.size}")
        viewModel.updateCloudAnchors(nodes)
    }

    LaunchedEffect(pathNode) {
        Log.d("ArView", "pathNode: $pathNode")
        if (pathNode != null)
            viewModel.drawPath(arSceneView, pathNode)
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            onMessage(uiState.message!!)
            viewModel.clearMessage()
        }
    }

    // Chúng ta cần quản lý vòng đời của ArSceneView (resume, pause, destroy)
    // và tạo ra Session ARCore.
    DisposableEffect(lifecycleOwner, arSceneView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume(context, arSceneView)
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause(arSceneView)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onDestroyView(arSceneView) // Hủy hoàn toàn khi Composable bị hủy
        }
    }
    DisposableEffect(arSceneView) {
        val updateListener = com.google.ar.sceneform.Scene.OnUpdateListener {
            // Chỉ chạy logic khi hệ thống AR đã sẵn sàng và không loading
            if (uiState.transformationSystem != null && !uiState.loading) {
                val frame = arSceneView.arFrame
                if (frame != null) {
                    val cameraPose = frame.camera.pose

                    // Cập nhật vị trí user trên bản đồ 2D
                    val location = viewModel.updateUserLocationFromWorld(cameraPose)

                    if (location != null) {
                        updateUserLocation(location)
                    } else {
                        // Fallback...
                    }
                }
                // Gọi ViewModel update loop
                viewModel.onUpdate(arSceneView)
            }
        }

        arSceneView.scene.addOnUpdateListener(updateListener)

        onDispose {
            arSceneView.scene.removeOnUpdateListener(updateListener)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // AndroidView để hiển thị ArSceneView
        AndroidView(
            factory = { arSceneView }, // Chỉ tạo view
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Khối update này sẽ chạy lại khi `uiState` thay đổi
                if (uiState.transformationSystem != null && !uiState.loading && uiState.error == null) {
                    // Sẵn sàng! Bật renderer và gán listener
                    view.planeRenderer.isVisible = true
                    view.planeRenderer.isEnabled = true
                }
            }
        )

        // Hiển thị UI overlay dựa trên trạng thái (State)
        when {
            uiState.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)) // Nền tối bán trong suốt
                        .clickable(enabled = false) {} // Chặn touch xuống map khi đang loading
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.message ?: "Đang xử lý...", // Hiện message loading
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            uiState.error != null -> {
                Text(
                    text = "Lỗi: ${uiState.error}",
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }
}
