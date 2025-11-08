package ie.app.minimap.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.sceneform.ArSceneView
import ie.app.minimap.MiniMapViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import ie.app.minimap.ArUiState

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ArScreen(
    viewModel: MiniMapViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val arSceneView = remember {
        ArSceneView(context).apply {
            planeRenderer.isVisible = true
            planeRenderer.isEnabled = true
        }
    }
    // Lấy LifecycleOwner
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

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
            viewModel.onDestroy(arSceneView) // Hủy hoàn toàn khi Composable bị hủy
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // AndroidView để hiển thị ArSceneView
        AndroidView(
            factory = { arSceneView }, // Chỉ tạo view
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Khối update này sẽ chạy lại khi `uiState` thay đổi
                when (val state = uiState) {
                    is ArUiState.Ready -> {
                        // Sẵn sàng! Bật renderer và gán listener
                        view.planeRenderer.isVisible = true
                        view.planeRenderer.isEnabled = true

                        view.scene.setOnTouchListener { hitTestResult, motionEvent ->
                            // Gửi sự kiện chạm đến ViewModel
                            viewModel.onSceneTouched(view, hitTestResult, motionEvent)
                            true // Đã xử lý
                        }

                        view.scene.addOnUpdateListener {
                            // Cập nhật TransformationSystem mỗi frame
//                            val frame = view.arFrame
//                            if (frame != null) {
//                                val cameraPose = frame.camera.pose
//                                // Trích xuất tọa độ [x, y, z]
//                                Log.d("MiniMap", "Camera Pose: ${cameraPose.tx()} ${cameraPose.ty()} ${cameraPose.tz()} ${cameraPose.qx()} ${cameraPose.qy()} ${cameraPose.qz()} ${cameraPose.qw()}")
//                            }
                        }
                    }
                    else -> {
                        // Chưa sẵn sàng (Loading/Error), không làm gì cả
                    }
                }
            }
        )

        // Hiển thị UI overlay dựa trên trạng thái (State)
        when (val state = uiState) {
            is ArUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                Text(
                    text = "Đang tải mô hình 3D...",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
            is ArUiState.Error -> {
                Text(
                    text = "Lỗi: ${state.message}",
                    color = androidx.compose.ui.graphics.Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            is ArUiState.Ready -> {
                Text("Nhấn vào mặt phẳng để đặt Cube")
            }
        }
    }
}
