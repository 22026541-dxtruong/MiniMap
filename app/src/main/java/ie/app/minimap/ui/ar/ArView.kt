package ie.app.minimap.ui.ar

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ArView(
    editing: Boolean,
    venueId: Long,
    building: Building,
    floor: Floor,
    nodes: List<Node>,
    onMessage: (String) -> Unit,
    pathNode: List<Node>? = null,
    updateUserLocation: (Offset?) -> Unit = {},
    viewModel: ArViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
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
    // Lấy LifecycleOwner
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var openDialog by remember { mutableStateOf(false) }
    var pendingHitPose by remember { mutableStateOf<Pose?>(null) }

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

                    view.scene.setOnTouchListener { hitTestResult, motionEvent ->
                        // Gửi sự kiện chạm đến ViewModel
                        if (!editing) return@setOnTouchListener false
                        val isMapEmpty = nodes.isEmpty()
                        val isReady = uiState.isLocalized

                        if (!isReady && !isMapEmpty) {
                            // Nếu chưa định vị mà map đã có dữ liệu -> Chặn và báo lỗi
                            if (motionEvent.action == MotionEvent.ACTION_UP) {
                                Toast.makeText(context, "⚠️ Vui lòng quét xung quanh để định vị trước khi chỉnh sửa!", Toast.LENGTH_SHORT).show()
                            }
                            return@setOnTouchListener true // Chặn event
                        }

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

            uiState.transformationSystem != null && !uiState.loading && uiState.error == null -> {

                if (openDialog) {
                    AddAnchorDialog(
                        onDismiss = { openDialog = false },
                        onConfirm = { type, name, description, vendorName, vendorDescription ->

                            if (pendingHitPose != null) {
                                viewModel.onSceneTouched(
                                    arSceneView,
                                    pose = pendingHitPose!!,
                                    type,
                                    name,
                                    description,
                                    vendorName,
                                    vendorDescription,
                                    floor,
                                    building,
                                    venueId
                                )
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
    onConfirm: (type: String, name: String?, description: String?, vendorName: String?, vendorDescription: String?) -> Unit = { _, _, _, _, _ -> },
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var vendorDescription by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(Node.INTERSECTION) }

    val nodeTypes = listOf(
        Node.ROOM,
        Node.INTERSECTION,
        Node.BOOTH,
        Node.CONNECTOR,
        Node.HALLWAY
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
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

                AnimatedContent(
                    targetState = selectedType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    when (it) {
                        Node.ROOM -> {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Room's name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                        }

                        Node.BOOTH -> {
                            Column {
                                Text(
                                    "Booth",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Booth's Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    minLines = 2,
                                    label = { Text("Booth's Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                                Text(
                                    "Vendor",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                OutlinedTextField(
                                    value = vendorName,
                                    onValueChange = { vendorName = it },
                                    label = { Text("Vendor's Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = vendorDescription,
                                    onValueChange = { vendorDescription = it },
                                    minLines = 2,
                                    label = { Text("Vendor's Description") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            when (selectedType) {
                                Node.ROOM -> {
                                    onConfirm(selectedType, name, null, null, null)
                                }

                                Node.BOOTH -> {
                                    onConfirm(
                                        selectedType,
                                        name,
                                        description,
                                        vendorName,
                                        vendorDescription
                                    )
                                }

                                else -> {
                                    onConfirm(selectedType, null, null, null, null)
                                }
                            }
                        },
                        enabled = when (selectedType) {
                            Node.ROOM -> name.isNotBlank()
                            Node.BOOTH -> name.isNotBlank() && description.isNotBlank()
                            else -> true
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
