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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@SuppressLint("ClickableViewAccessibility", "LocalContextResourcesRead")
@Composable
fun ArViewEditor(
    venueId: Long,
    building: Building,
    floor: Floor,
    nodes: List<Node>,
    onMessage: (String) -> Unit,
    updateUserLocation: (Offset?) -> Unit = {},
    viewModel: ArViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var openDialog by remember { mutableStateOf(false) }
    var pendingHitPose by remember { mutableStateOf<Pose?>(null) }
    val currentNodes by rememberUpdatedState(nodes)
    val currentUiState by rememberUpdatedState(uiState)

    val isLoading by remember { derivedStateOf { uiState.loading } }
    val errorMessage by remember { derivedStateOf { uiState.error } }
    val currentMessage by remember { derivedStateOf { uiState.message } }

    val arSceneView = remember {
        ArSceneView(context).apply {
            planeRenderer.isVisible = true
            planeRenderer.isEnabled = true
            scene.setOnTouchListener { _, motionEvent ->
                val isMapEmpty = currentNodes.isEmpty()
                val isReady = currentUiState.isLocalized

                if (!isReady && !isMapEmpty) {
                    if (motionEvent.action == MotionEvent.ACTION_UP) {
                        Toast.makeText(context, "⚠️ Vui lòng quét xung quanh để định vị!", Toast.LENGTH_SHORT).show()
                    }
                    return@setOnTouchListener true
                }

                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    val frame = arFrame ?: return@setOnTouchListener true
                    val hits = frame.hitTest(motionEvent)
                    val hit = hits.firstOrNull { hitResult ->
                        val trackable = hitResult.trackable
                        trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
                    }

                    if (hit != null) {
                        pendingHitPose = hit.hitPose
                        openDialog = true
                    }
                }
                true
            }
        }
    }

    LaunchedEffect(nodes) {
        viewModel.updateCloudAnchors(nodes)
    }

    LaunchedEffect(currentMessage) {
        if (currentMessage != null) {
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
                            text = currentMessage ?: "Đang xử lý...",
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

            else -> {
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
@Composable
private fun AddAnchorDialog(
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
//                    .animateContentSize() // Giúp Dialog co giãn mượt mà
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
                                    // Reset fields khi đổi loại để mượt hơn
                                    name = ""
                                    description = ""
                                    vendorName = ""
                                    vendorDescription = ""
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                // Tách biệt nội dung dựa trên loại đã chọn
                AnimatedContent(
                    targetState = selectedType,
                    label = "FieldTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { targetType ->
                    when (targetType) {
                        Node.ROOM -> {
                            RoomAnchorFields(
                                name = name,
                                onNameChange = { name = it }
                            )
                        }

                        Node.BOOTH -> {
                            BoothAnchorFields(
                                name = name, onNameChange = { name = it },
                                description = description, onDescriptionChange = { description = it },
                                vendorName = vendorName, onVendorNameChange = { vendorName = it },
                                vendorDescription = vendorDescription, onVendorDescriptionChange = { vendorDescription = it }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            when (selectedType) {
                                Node.ROOM -> onConfirm(selectedType, name, null, null, null)
                                Node.BOOTH -> onConfirm(selectedType, name, description, vendorName, vendorDescription)
                                else -> onConfirm(selectedType, null, null, null, null)
                            }
                        },
                        enabled = when (selectedType) {
                            Node.ROOM -> name.isNotBlank()
                            Node.BOOTH -> name.isNotBlank() && description.isNotBlank() && vendorName.isNotBlank()
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

@Composable
private fun RoomAnchorFields(
    name: String,
    onNameChange: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Room's Name") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}

@Composable
private fun BoothAnchorFields(
    name: String, onNameChange: (String) -> Unit,
    description: String, onDescriptionChange: (String) -> Unit,
    vendorName: String, onVendorNameChange: (String) -> Unit,
    vendorDescription: String, onVendorDescriptionChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Booth Info", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("Booth's Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description, onValueChange = onDescriptionChange,
            label = { Text("Booth's Description") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Vendor Info", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vendorName, onValueChange = onVendorNameChange,
            label = { Text("Vendor's Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = vendorDescription, onValueChange = onVendorDescriptionChange,
            label = { Text("Vendor's Description") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
    }
}

