package ie.app.minimap.ui.screens.editor

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.ui.components.ArEditor
import kotlin.collections.forEach

@Composable
fun MapEditorScreen(
    venueId: Long,
    viewModel: MapEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val buildings by viewModel.buildings.collectAsState()
    val floors by viewModel.floors.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val draggingState by viewModel.draggingState.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val selection by viewModel.selection.collectAsState() // Theo dõi lựa chọn
    val textPaint = remember {
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
    }

    LaunchedEffect(venueId) {
        viewModel.loadMap(venueId)
    }

    Scaffold { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {

            Box(Modifier.fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF0F0F0))
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                viewModel.onZoom(centroid, zoom)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { viewModel.onTap(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = viewModel::onDragStart,
                                onDrag = { change, dragAmount ->
                                    viewModel.onDragGesture(change, dragAmount)
                                    change.consume()
                                },
                                onDragEnd = viewModel::onDragEnd,
                                onDragCancel = viewModel::onDragEnd
                            )
                        }
                ) {
                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale = scale, pivot = Offset.Zero)
                    }) {
                        drawEdges(edges, nodes, selection)
                        drawDraggingLine(draggingState)
                        drawNodes(nodes, textPaint, draggingState, selection)
                        drawUserPosition(userPosition)
                    }
                }

                Row(
                    Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (selection != Selection.None) {
                        FilledTonalIconButton(onClick = viewModel::deleteSelection) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = { viewModel.zoom(1.2f) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in"
                        )
                    }
                    FilledTonalIconButton(onClick = { viewModel.zoom(0.8f) }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_check_indeterminate_small_24),
                            contentDescription = "Zoom out"
                        )
                    }
                }

                FloorsDropDownMenu(
                    modifier = Modifier.align(Alignment.TopStart),
                    floors = floors,
                    selectedFloor = uiState.selectedFloor,
                    onFloorSelected = viewModel::onFloorSelected
                )

                BuildingsDropDownMenu(
                    modifier = Modifier.align(Alignment.BottomStart),
                    buildings = buildings,
                    selectedBuilding = uiState.selectedBuilding,
                    onBuildingSelected = viewModel::onBuildingSelected
                )
            }

        ArEditor(
            floorId = uiState.selectedFloor.id,
            updateUserLocation = { viewModel.updateUserLocation(it.x, it.y) },
            modifier = Modifier.fillMaxHeight(0.6f)
            .align(Alignment.TopCenter))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorsDropDownMenu(
    floors: List<Floor>,
    selectedFloor: Floor,
    onFloorSelected: (Floor) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(8.dp)
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .width(100.dp)
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        color = Color.Gray, // Hoặc dùng màu Material
                        shape = RoundedCornerShape(4.dp) // Hình bo góc
                    )
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_stacks_24),
                    contentDescription = "Floors",
                    modifier = Modifier.size(24.dp).padding(4.dp)
                )
                Text(
                    selectedFloor.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            }

            // ExposedDropdownMenu có khả năng xử lý danh sách cuộn tốt hơn
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                floors.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption.name) },
                        onClick = {
                            onFloorSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        FilledTonalIconButton(
            onClick = {  },
        ) {
            Icon(
                painter = painterResource(R.drawable.stacks_add_24dp),
                contentDescription = "Show Floors",
                modifier = Modifier.size(24.dp).padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingsDropDownMenu(
    buildings: List<Building> = (1..10).toList().map { Building(it.toLong(), name = "Tòa nhà $it") },
    selectedBuilding: Building = Building(1, name = "Tòa nhà 1"),
    onBuildingSelected: (Building) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(8.dp)
            .height(50.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = {  },
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_domain_add_24),
                contentDescription = "Show Floors",
                modifier = Modifier.size(24.dp).padding(4.dp)
            )
        }
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(100.dp)
                .border(
                    width = 2.dp,
                    color = Color.Gray, // Hoặc dùng màu Material
                    shape = RoundedCornerShape(4.dp) // Hình bo góc
                ).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_domain_24),
                contentDescription = "Buildings",
                modifier = Modifier.size(24.dp).padding(4.dp)
            )
            Text(
                selectedBuilding.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = expanded,
                modifier = Modifier.rotate(if (expanded) 90f else -270f)
            )
        }
        // ExposedDropdownMenu có khả năng xử lý danh sách cuộn tốt hơn
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        ) {
            // LazyRow để hiển thị danh sách các building
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(buildings, key = { it.id }) { building ->
                    FilledTonalButton(
                        onClick = {
                            onBuildingSelected(building)
                            expanded = false // Khi chọn 1 item, đóng dropdown
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            building.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// --- Các hàm trợ giúp vẽ ---

private fun DrawScope.drawEdges(
    edges: List<Edge>,
    nodes: List<Node>,
    selection: Selection
) {
    edges.forEach { edge ->
        val startNode = nodes.find { it.id == edge.fromNode }
        val endNode = nodes.find { it.id == edge.toNode }

        if (startNode != null && endNode != null) {
            val isSelected = (selection as? Selection.EdgeSelected)?.id == edge.id

            drawLine(
                color = if (isSelected) Color.Red else Color.Black,
                start = Offset(startNode.x, startNode.y),
                end = Offset(endNode.x, endNode.y),
                strokeWidth = if (isSelected) 10f else 5f
            )
        }
    }
}

private fun DrawScope.drawDraggingLine(draggingState: DraggingState?) {
    draggingState?.let { drag ->
        val startPos = Offset(drag.startNode.x, drag.startNode.y)
        // Nếu "dính" thì vẽ đến tâm nút, nếu không thì vẽ đến ngón tay
        val endPos = Offset(
            drag.snapTargetNode?.x ?: drag.currentPosition.x,
            drag.snapTargetNode?.y ?: drag.currentPosition.y
        )

        drawLine(
            color = Color.Blue.copy(alpha = 0.7f),
            start = startPos,
            end = endPos,
            strokeWidth = 6f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
        )
    }
}

private fun DrawScope.drawNodes(
    nodes: List<Node>,
    textPaint: Paint,
    draggingState: DraggingState?,
    selection: Selection
) {
    nodes.forEach { node ->
        // Kiểm tra xem nút này có đang được chọn không
        val isSelected = (selection as? Selection.NodeSelected)?.id == node.id

        // Vẽ vòng tròn chính của nút
        drawCircle(
            color = Color(0xFF3B82F6), // Màu xanh dương
            center = Offset(node.x, node.y),
            radius = MapEditorViewModel.RADIUS
        )

        // Vẽ viền
        drawCircle(
            color = if (isSelected) Color.Red else Color.Black, // Màu viền đỏ nếu được chọn
            center = Offset(node.x, node.y),
            radius = MapEditorViewModel.RADIUS,
            style = Stroke(width = if (isSelected) 8f else 3f) // Viền dày hơn nếu được chọn
        )

        // Vẽ nhãn (label)
        drawContext.canvas.nativeCanvas.drawText(
            node.label,
            node.x,
            node.y - (textPaint.descent() + textPaint.ascent()) / 2, // Căn giữa
            textPaint
        )

        // Vẽ vòng "dính" (snap ring) nếu nút này là mục tiêu
        if (draggingState?.snapTargetNode?.id == node.id) {
            drawCircle(
                color = Color.Green.copy(alpha = 0.5f),
                center = Offset(node.x, node.y),
                radius = MapEditorViewModel.SNAP_THRESHOLD,
                style = Stroke(
                    width = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            )
        }
    }
}

private fun DrawScope.drawUserPosition(position: Offset?) {
    if (position == null) return

    val pulseRadius = 50f // Bán kính vòng tỏa
    val dotRadius = 25f   // Bán kính chấm người dùng

    // Vẽ vòng tròn mờ xung quanh (mô phỏng độ chính xác)
    drawCircle(
        color = Color.Magenta.copy(alpha = 0.3f),
        center = position,
        radius = pulseRadius
    )

    // Vẽ viền trắng cho nổi bật
    drawCircle(
        color = Color.White,
        center = position,
        radius = dotRadius + 5f
    )

    // Vẽ chấm chính (Màu Tím hồng để khác biệt với Node xanh dương)
    drawCircle(
        color = Color.Magenta,
        center = position,
        radius = dotRadius
    )
}