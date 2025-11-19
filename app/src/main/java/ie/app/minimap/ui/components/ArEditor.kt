package ie.app.minimap.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import ie.app.minimap.data.local.entity.Node

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ArEditor(
    floorId: Long,
    updateUserLocation: (Offset) -> Unit = {},
    viewModel: ArViewModel = hiltViewModel(),
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

    var openDialog by remember { mutableStateOf(false) }
    var pendingHitPose by remember { mutableStateOf<Pose?>(null) }


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
                            if (motionEvent.action == MotionEvent.ACTION_UP) {
                                val frame = view.arFrame ?: return@setOnTouchListener true

                                // Hit test màn hình tại vị trí chạm
                                val hits = frame.hitTest(motionEvent)

                                // Tìm plane hợp lệ
                                val hit = hits.firstOrNull { hitResult ->
                                    val trackable = hitResult.trackable
                                    trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
                                }

                                if (hit != null) {
                                    // Lưu event và mở dialog
                                    pendingHitPose = hit.hitPose
                                    openDialog = true
                                }
                            }

                            true // Đã xử lý
                        }

                        view.scene.addOnUpdateListener {
                            // Cập nhật TransformationSystem mỗi frame
                            val frame = view.arFrame
                            if (frame != null) {
                                val cameraPose = frame.camera.pose
                                updateUserLocation(viewModel.worldToCanvas(cameraPose.tx(), cameraPose.tz()))
                                // Trích xuất tọa độ [x, y, z]
                                Log.d("MiniMap", "Camera Pose: ${cameraPose.tx()} ${cameraPose.ty()} ${cameraPose.tz()} ${cameraPose.qx()} ${cameraPose.qy()} ${cameraPose.qz()} ${cameraPose.qw()}")
                            }
//                            viewModel.onUpdate(view)

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
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            is ArUiState.Ready -> {
                Button(onClick = {
                    viewModel.exportCloudAnchorsToFile(context = context)
                }) {
                    Text(text = "Export Anchors")
                }

                if (openDialog) {
                    AddAnchorDialog(
                        onDismiss = { openDialog = false },
                        onConfirm = { name, type ->

                            if (pendingHitPose != null) {
                                viewModel.onSceneTouched(arSceneView, pose = pendingHitPose!!, name, type, floorId)
                            }
                            openDialog = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun AddAnchorDialog(
    onDismiss: () -> Unit = {},
    onConfirm: (String, String) -> Unit = { _, _ -> },
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(Node.STAIRS) }

    val nodeTypes = listOf(
        Node.ROOM,
        Node.HALLWAY,
        Node.CONNECTOR,
        Node.STAIRS,
        Node.ELEVATOR
    )

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .width(IntrinsicSize.Min)
            ) {

                Text(
                    text = "Add Anchor",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Anchor name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Anchor type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )

                    // ExposedDropdownMenu có khả năng xử lý danh sách cuộn tốt hơn
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        nodeTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedType = selectionOption
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            onConfirm(text, selectedType)
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
