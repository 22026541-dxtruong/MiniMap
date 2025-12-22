package ie.app.minimap.ui.graph

import android.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape
import ie.app.minimap.data.local.relations.NodeWithShape
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlin.collections.forEach
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GraphEditor(
    venueId: Long = 1,
    floorId: Long = 1,
    userPosition: State<Offset?>,
    centerNode: NodeWithShape? = null,
    onCenterConsumed: () -> Unit = { },
    onSelectionConsumed: (NodeWithShape?) -> Unit = { },
    onEditNode: (NodeWithShape) -> Unit = {},
    viewModel: GraphViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val draggingState by viewModel.draggingState.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val editorMode by viewModel.editorMode.collectAsState()
    val alignmentGuides by viewModel.alignmentGuides.collectAsState()

    val textPaint = remember {
        Paint().apply {
            color = Color.White.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 28f
        }
    }

    LaunchedEffect(floorId) {
        viewModel.loadGraph(floorId)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { userPosition.value }
            .filterNotNull()
            .collect { pos ->
                viewModel.centerOnNode(pos)
                this.cancel()
            }
    }

    LaunchedEffect(centerNode) {
        centerNode?.let {
            viewModel.centerOnNode(Offset(centerNode.node.x, centerNode.node.y))
            viewModel.updateSelection(Selection.NodeSelected(centerNode))
            onCenterConsumed()
        }
    }

    LaunchedEffect(selection) {
        val selected = if (selection is Selection.NodeSelected) (selection as Selection.NodeSelected).nodeWithShape else null
        onSelectionConsumed(selected)
    }

    Box(modifier.fillMaxSize()) {
        Spacer( // Dùng Spacer thay vì Canvas vì ta sẽ tự vẽ bằng modifier
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .onSizeChanged { size -> viewModel.setScreenSize(size) }
                // --- BẮT ĐẦU PHẦN QUAN TRỌNG: drawWithCache ---
                .drawWithCache {
                    // 1. KHỞI TẠO OBJECT (Chỉ chạy 1 lần hoặc khi size đổi)
                    // Tại đây, chúng ta tạo các biến dùng chung để tránh tạo rác bộ nhớ
                    val sharedPath = Path()
                    val sharedVertices = ArrayList<Offset>(6) // Capacity 6 (đủ cho lục giác)

                    // 2. KHỐI VẼ (Chạy mỗi frame - 60 lần/giây)
                    onDrawBehind {
                        // Gọi hàm withTransform như cũ
                        withTransform({
                            translate(left = offset.x, top = offset.y)
                            rotate(degrees = rotation, pivot = Offset.Zero)
                            scale(scale = scale, pivot = Offset.Zero)
                        }) {
                            // Truyền sharedPath và sharedVertices vào hàm vẽ
                            drawGraphContent(
                                nodes = nodes,
                                edges = edges,
                                textPaint = textPaint, // textPaint đã được remember ở trên
                                draggingState = draggingState,
                                selection = selection,
                                scale = scale,
                                editorMode = editorMode,
                                userPosition = userPosition.value,
                                alignmentGuides = alignmentGuides,
                                viewModel = viewModel,
                                reusablePath = sharedPath,         // <--- Truyền vào đây
                                reusableVertices = sharedVertices  // <--- Truyền vào đây
                            )
                        }
                    }
                }
                // --- CÁC GESTURE DETECTOR GIỮ NGUYÊN ---
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        viewModel.onTransform(centroid, pan, zoom, rotation)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.onTap(it) })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { viewModel.onDragStart(it) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            viewModel.onDragGesture(change, dragAmount)
                        },
                        onDragEnd = { viewModel.onDragEnd(venueId, floorId) },
                        onDragCancel = { viewModel.onDragEnd(venueId, floorId) }
                    )
                }
        )
        GraphControls(
            modifier = Modifier.padding(8.dp).align(Alignment.TopEnd),
            selection = selection,
            userPosition = userPosition.value,
            editorMode = editorMode,
            viewModel = viewModel
        )

        if (selection is Selection.NodeSelected) {
            Text(
                text = (selection as Selection.NodeSelected).nodeWithShape.node.type,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }

        if (selection is Selection.NodeSelected && (editorMode == EditorMode.MOVE || editorMode == EditorMode.SCALE)) {
            PropertiesPanel(
                (selection as Selection.NodeSelected).nodeWithShape,
                editorMode,
                viewModel,
                onEdit = { onEditNode((selection as Selection.NodeSelected).nodeWithShape) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun GraphControls(
    modifier: Modifier,
    selection: Selection,
    userPosition: Offset?,
    editorMode: EditorMode,
    viewModel: GraphViewModel
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            if (selection != Selection.None) {
                FilledTonalIconButton(onClick = viewModel::deleteSelection) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                }
            }
            FilledTonalIconButton(onClick = { viewModel.zoom(1.2f) }) {
                Icon(Icons.Default.Add, "Zoom in")
            }
            FilledTonalIconButton(onClick = { viewModel.zoom(0.8f) }) {
                Icon(painterResource(R.drawable.outline_check_indeterminate_small_24), "Zoom out")
            }
        }
        if (userPosition != null) {
            FilledTonalIconButton(
                onClick = { viewModel.centerOnNode(userPosition) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_my_location_24),
                    contentDescription = "User Position",
                )
            }
        }
        if (selection is Selection.NodeSelected && selection.nodeWithShape.node.type != Node.HALLWAY) {
            FilledTonalIconButton(
                onClick = { viewModel.toggleMode(EditorMode.CONNECT) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (editorMode == EditorMode.CONNECT) MaterialTheme.colorScheme.tertiaryContainer else Color.Unspecified
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_route_24),
                    contentDescription = "Connect",
                )
            }
            if (selection.nodeWithShape.node.type in listOf(Node.ROOM, Node.BOOTH)) {
                FilledTonalIconButton(
                    onClick = {
                        viewModel.toggleMode(EditorMode.MOVE)
                        viewModel.addShapeNearNode()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (editorMode == EditorMode.SCALE || editorMode == EditorMode.MOVE) MaterialTheme.colorScheme.tertiaryContainer else Color.Unspecified
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_shapes_24),
                        contentDescription = "Shape"
                    )
                }
            }
        }
    }
}

@Composable
fun PropertiesPanel(
    selectedNode: NodeWithShape,
    editorMode: EditorMode,
    viewModel: GraphViewModel,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { viewModel.toggleMode(EditorMode.MOVE) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (editorMode == EditorMode.MOVE) MaterialTheme.colorScheme.tertiaryContainer else Color.Unspecified
                )
            ) { Icon(painterResource(R.drawable.outline_drag_pan_24), "Move") }

            FilledTonalIconButton(
                onClick = { viewModel.toggleMode(EditorMode.SCALE) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (editorMode == EditorMode.SCALE) MaterialTheme.colorScheme.tertiaryContainer else Color.Unspecified
                )
            ) { Icon(painterResource(R.drawable.outline_open_in_full_24), "Scale") }

            FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
        }

        // Color Picker - Tối ưu: Dùng drawBehind thay vì clip()
        Text("Color:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(viewModel.availableColors) { color ->
                val isSelected = selectedNode.shape?.color == color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .drawBehind {
                            drawCircle(Color(color)) // Vẽ màu nền
                            if (isSelected) {
                                drawCircle(Color.Black, style = Stroke(width = 3.dp.toPx()))
                            }
                        }
                        .clickable { viewModel.updateSelectedNodeColor(color) }
                )
            }
        }

        Text("Shape:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Shape.Companion.ShapeType.entries.toTypedArray()) { shape ->
                val isSelected = selectedNode.shape?.shape == shape
                val tintColor = if (isSelected) Color(0xFF3B82F6) else Color.Gray

                Box(
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = tintColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (isSelected) Color(0xFFEFF6FF) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.updateSelectedNodeShape(shape) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    ShapeIcon(shape = shape, color = tintColor, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun ShapeIcon(shape: Shape.Companion.ShapeType, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h / 2)
        val strokeStyle = Stroke(width = 2.dp.toPx())

        when (shape) {
            Shape.Companion.ShapeType.RECTANGLE -> {
                drawRoundRect(
                    color = color,
                    size = size,
                    cornerRadius = CornerRadius(w * 0.15f),
                    style = strokeStyle
                )
            }
//            Shape.Companion.ShapeType.CAPSULE -> {
//                drawRoundRect(color = color, size = size, cornerRadius = CornerRadius(min(w, h) / 2), style = strokeStyle)
//            }
            Shape.Companion.ShapeType.CIRCLE -> {
                drawOval(color = color, size = size, style = strokeStyle)
            }

            Shape.Companion.ShapeType.TRIANGLE, Shape.Companion.ShapeType.DIAMOND, Shape.Companion.ShapeType.PENTAGON, Shape.Companion.ShapeType.HEXAGON -> {
                val path = Path()
                val radiusX = w / 2
                val radiusY = h / 2
                val vertices = mutableListOf<Offset>()

                when (shape) {
                    Shape.Companion.ShapeType.TRIANGLE -> {
                        vertices.add(Offset(center.x, 0f))
                        vertices.add(Offset(w, h))
                        vertices.add(Offset(0f, h))
                    }
                    Shape.Companion.ShapeType.DIAMOND -> {
                        vertices.add(Offset(center.x, 0f))
                        vertices.add(Offset(w, center.y))
                        vertices.add(Offset(center.x, h))
                        vertices.add(Offset(0f, center.y))
                    }
                    else -> {
                        val sides = if (shape == Shape.Companion.ShapeType.PENTAGON) 5 else 6
                        val startAngle = -PI / 2
                        for (i in 0 until sides) {
                            val angle = startAngle + 2 * PI * i / sides
                            vertices.add(
                                Offset(
                                    center.x + (radiusX * cos(angle)).toFloat(),
                                    center.y + (radiusY * sin(angle)).toFloat()
                                )
                            )
                        }
                    }
                }
                path.addRoundedPolygon(vertices, 0.25f)
                drawPath(path = path, color = color, style = strokeStyle)
            }
        }
    }
}

private fun DrawScope.drawGraphContent(
    nodes: List<NodeWithShape>,
    edges: List<Edge>,
    textPaint: Paint,
    draggingState: DraggingState?,
    selection: Selection,
    scale: Float,
    editorMode: EditorMode,
    userPosition: Offset?,
    alignmentGuides: List<AlignmentGuide>,
    viewModel: GraphViewModel,
    reusablePath: Path,
    reusableVertices: ArrayList<Offset>
) {
    // Layer 1: Shapes (Building/Rooms)
    drawShapes(nodes, textPaint, draggingState, selection, scale, editorMode, reusablePath, reusableVertices)

    // Layer 2: Edges (Connections)
    drawEdges(edges, viewModel, selection, scale)

    // Layer 3: Dragging Line
    drawDraggingLine(draggingState, scale)

    // Layer 4: Dots (Intersection/Nodes) - Vẽ đè lên shape
    drawNodes(nodes, draggingState, selection, scale)

    // Layer 5: User Position
    if (userPosition != null) {
        drawUserPosition(userPosition, scale)
    }

    // Layer 6: Guides
    drawAlignmentGuides(alignmentGuides, scale)
}

private fun DrawScope.drawAlignmentGuides(guides: List<AlignmentGuide>, scale: Float) {
    val guideColor = Color(0xFFFF00FF)
    val strokeWidth = 1.5f / scale
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 5f / scale), 0f)
    guides.forEach { guide ->
        if (guide.isVertical) {
            drawLine(
                color = guideColor,
                start = Offset(guide.coordinate, guide.start),
                end = Offset(guide.coordinate, guide.end),
                strokeWidth = strokeWidth,
                pathEffect = pathEffect
            )
        } else {
            drawLine(
                color = guideColor,
                start = Offset(guide.start, guide.coordinate),
                end = Offset(guide.end, guide.coordinate),
                strokeWidth = strokeWidth,
                pathEffect = pathEffect
            )
        }
    }
}

private fun DrawScope.drawEdges(
    edges: List<Edge>,
    viewModel: GraphViewModel,
    selection: Selection,
    scale: Float
) {
    val baseStrokeWidth = 5f
    val selectedStrokeWidth = 10f
    edges.forEach { edge ->
        val startNode = viewModel.getNodeById(edge.fromNode)
        val endNode = viewModel.getNodeById(edge.toNode)
        if (startNode != null && endNode != null) {
            val isSelected = (selection as? Selection.EdgeSelected)?.edge?.id == edge.id
            val currentStrokeWidth =
                (if (isSelected) selectedStrokeWidth else baseStrokeWidth) / scale
            drawLine(
                color = if (isSelected) Color.Black else Color.Gray,
                start = Offset(startNode.node.x, startNode.node.y),
                end = Offset(endNode.node.x, endNode.node.y),
                strokeWidth = currentStrokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawDraggingLine(draggingState: DraggingState?, scale: Float) {
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
            strokeWidth = 6f / scale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f / scale, 10f / scale), 0f),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

fun Path.addRoundedPolygon(vertices: List<Offset>, cornerFraction: Float = 0.25f) {
    if (vertices.size < 3) return
    val n = vertices.size
    val v0 = vertices[0]
    val vPrev = vertices[n - 1]
    val start0 = v0 + (vPrev - v0) * cornerFraction
    moveTo(start0.x, start0.y)
    for (i in 0 until n) {
        val curr = vertices[i]
        val next = vertices[(i + 1) % n]
        val endCurr = curr + (next - curr) * cornerFraction
        quadraticTo(curr.x, curr.y, endCurr.x, endCurr.y)
        val startNext = next + (curr - next) * cornerFraction
        lineTo(startNext.x, startNext.y)
    }
    close()
}

private fun DrawScope.drawShapes(
    nodeWithShapes: List<NodeWithShape>,
    textPaint: Paint,
    draggingState: DraggingState?,
    selection: Selection,
    scale: Float,
    editorMode: EditorMode,
    reusablePath: Path,          // Nhận Path tái sử dụng
    reusableVertices: ArrayList<Offset> // Nhận List tái sử dụng
) {
    nodeWithShapes.forEach { node ->
        val shapeData = node.shape ?: return@forEach // Fast return

        val w = shapeData.width
        val h = shapeData.height
        val cx = shapeData.centerX
        val cy = shapeData.centerY

        val isSelected = (selection as? Selection.NodeSelected)?.nodeWithShape?.node?.id == node.node.id
        val isSnapTarget = draggingState?.snapTargetNode?.id == node.node.id

        // Pre-calculate colors/styles
        val fillColor = if (isSnapTarget) Color(shapeData.color).copy(alpha = 0.8f) else Color(shapeData.color)
        val strokeColor = if (isSelected) Color.Red else Color.Black
        val strokeWidth = (if (isSelected) 6f else 2f) / scale
        val strokeStyle = Stroke(width = strokeWidth)

        // Reset path cho lần vẽ này
        reusablePath.reset()

        when (shapeData.shape) {
            Shape.Companion.ShapeType.RECTANGLE -> {
                val cornerRadius = CornerRadius(16f / scale, 16f / scale)
                val topLeft = Offset(cx - w / 2, cy - h / 2)
                val size = Size(w, h)
                drawRoundRect(color = fillColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius)
                drawRoundRect(color = strokeColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius, style = strokeStyle)
            }
            Shape.Companion.ShapeType.CIRCLE -> {
                val topLeft = Offset(cx - w / 2, cy - h / 2)
                val size = Size(w, h)
                drawOval(color = fillColor, topLeft = topLeft, size = size)
                drawOval(color = strokeColor, topLeft = topLeft, size = size, style = strokeStyle)
            }
            // Polygon Group
            else -> {
                reusableVertices.clear() // Xóa list cũ
                val radiusX = w / 2
                val radiusY = h / 2

                when (shapeData.shape) {
                    Shape.Companion.ShapeType.TRIANGLE -> {
                        reusableVertices.add(Offset(cx, cy - radiusY))
                        reusableVertices.add(Offset(cx + radiusX, cy + radiusY))
                        reusableVertices.add(Offset(cx - radiusX, cy + radiusY))
                    }
                    Shape.Companion.ShapeType.DIAMOND -> {
                        reusableVertices.add(Offset(cx, cy - radiusY))
                        reusableVertices.add(Offset(cx + radiusX, cy))
                        reusableVertices.add(Offset(cx, cy + radiusY))
                        reusableVertices.add(Offset(cx - radiusX, cy))
                    }
                    else -> { // PENTAGON, HEXAGON
                        val sides = if (shapeData.shape == Shape.Companion.ShapeType.PENTAGON) 5 else 6
                        val startAngle = -PI / 2
                        for (i in 0 until sides) {
                            val angle = startAngle + 2 * PI * i / sides
                            reusableVertices.add(
                                Offset(
                                    cx + (radiusX * cos(angle)).toFloat(),
                                    cy + (radiusY * sin(angle)).toFloat()
                                )
                            )
                        }
                    }
                }

                // Helper extension function dùng reusable path
                reusablePath.addRoundedPolygon(reusableVertices, cornerFraction = 0.25f)
                drawPath(path = reusablePath, color = fillColor)
                drawPath(path = reusablePath, color = strokeColor, style = strokeStyle)
            }
        }

        // Handle drawing (chỉ khi select + scale)
        if (isSelected && editorMode == EditorMode.SCALE) {
            drawResizeHandles(cx, cy, w, h, scale)
        }

        // Text Drawing
        if (shapeData.label.isNotEmpty()) {
            // Tối ưu: Chỉ tính offset text 1 lần nếu font không đổi, nhưng ở đây tạm chấp nhận
            val fontHeight = textPaint.descent() - textPaint.ascent()
            val textOffset = fontHeight / 2 - textPaint.descent()
            drawContext.canvas.nativeCanvas.drawText(
                shapeData.label,
                cx,
                cy + textOffset,
                textPaint
            )
        }
    }
}

private fun DrawScope.drawResizeHandles(cx: Float, cy: Float, w: Float, h: Float, scale: Float) {
    val handleRadius = 15f / scale
    val halfW = w / 2
    val halfH = h / 2
    val topLeft = Offset(cx - halfW, cy - halfH)

    // Boundary box
    drawRect(
        color = Color.Gray,
        topLeft = topLeft,
        size = Size(w, h),
        style = Stroke(
            width = 2f / scale,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 10f / scale))
        )
    )

    // 8 points
    val points = listOf(
        Offset(cx - halfW, cy - halfH), Offset(cx, cy - halfH), Offset(cx + halfW, cy - halfH),
        Offset(cx - halfW, cy), /* Center skipped */ Offset(cx + halfW, cy),
        Offset(cx - halfW, cy + halfH), Offset(cx, cy + halfH), Offset(cx + halfW, cy + halfH)
    )

    points.forEach { pos ->
        drawCircle(color = Color.White, radius = handleRadius, center = pos)
        drawCircle(color = Color.Black, radius = handleRadius, center = pos, style = Stroke(width = 2f / scale))
    }
}

fun DrawScope.drawNodes(
    nodeWithShapes: List<NodeWithShape>,
    draggingState: DraggingState?,
    selection: Selection,
    scale: Float
) {
    nodeWithShapes.forEach { nodeWithShape ->
        val node = nodeWithShape.node
        val isSelected = (selection as? Selection.NodeSelected)?.nodeWithShape?.node?.id == node.id
        val isSnapTarget = draggingState?.snapTargetNode?.id == node.id

        val topLeft = Offset(node.x, node.y) - Offset(
            GraphViewModel.DOT_RADIUS / scale,
            GraphViewModel.DOT_RADIUS / scale
        )
        val size = Size(GraphViewModel.DOT_RADIUS * 2 / scale, GraphViewModel.DOT_RADIUS * 2 / scale)

        val strokeColor = if (isSelected) Color.Red else Color.Black
        val strokeWidth = (if (isSelected) 6f else 2f) / scale
        val strokeStyle = Stroke(width = strokeWidth)
        val fillColor = if (isSnapTarget) Color(0x3B82F6CC) else Color(0xFF3B82F6)

        drawOval(color = if (node.type == Node.HALLWAY) Color.Green else fillColor, topLeft = topLeft, size = size)
        if (isSelected) drawOval(
            color = strokeColor,
            topLeft = topLeft,
            size = size,
            style = strokeStyle
        )
    }
}

fun DrawScope.drawUserPosition(userLocation: Offset?, scale: Float) {
    if (userLocation == null) return

    val dotRadius = 10f / scale

    // 1. Vẽ vòng tròn mờ (Độ chính xác)
    drawCircle(
        color = Color.Magenta.copy(alpha = 0.2f),
        center = userLocation,
        radius = 25f / scale
    )

    // 3. Vẽ chấm chính
    drawCircle(color = Color.White, center = userLocation, radius = dotRadius + (2f/scale))
    drawCircle(color = Color.Magenta, center = userLocation, radius = dotRadius)
}
