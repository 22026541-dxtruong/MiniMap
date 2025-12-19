package ie.app.minimap.ui.graph

import android.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape
import ie.app.minimap.data.local.relations.NodeWithShape
import kotlin.collections.forEach
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Preview
@Composable
fun GraphViewer(
    floorId: Long = 1,
    userPosition: Offset? = null,
    wayOffset: List<Offset>? = null,
    centerNode: NodeWithShape? = null,
    onCenterConsumed: () -> Unit = { },
    onSelectionConsumed: (NodeWithShape?) -> Unit = { },
    viewModel: GraphViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val nodes by viewModel.nodes.collectAsState()
    val draggingState by viewModel.draggingState.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val selection by viewModel.selection.collectAsState()

    val textPaint = remember {
        Paint().apply {
            color = Color.White.toArgb()
            textAlign = Paint.Align.CENTER
        }
    }

    LaunchedEffect(floorId) {
        viewModel.loadGraph(floorId)
    }

    LaunchedEffect(Unit) {
        if (userPosition != null) viewModel.centerOnNode(userPosition)
    }

    LaunchedEffect(centerNode) {
        if (centerNode != null) {
            viewModel.centerOnNode(Offset(centerNode.node.x, centerNode.node.y))
            viewModel.updateSelection(Selection.NodeSelected(centerNode))
            onCenterConsumed()
        }
    }

    LaunchedEffect(selection) {
        if (selection is Selection.NodeSelected) {
            onSelectionConsumed((selection as Selection.NodeSelected).nodeWithShape)
        } else {
            onSelectionConsumed(null)
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .onSizeChanged { size -> viewModel.setScreenSize(size) }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        viewModel.onTransform(centroid, pan, zoom, rotation)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.onTap(it) })
                }
        ) {
            withTransform({
                translate(left = offset.x, top = offset.y)
                rotate(degrees = rotation, pivot = Offset.Zero)
                scale(scale = scale, pivot = Offset.Zero)
            }) {
                drawShapes(nodes, textPaint, draggingState, selection, scale)
                drawNodes(nodes.filter { it.node.type != Node.HALLWAY }.map { it.node }, draggingState, selection, scale)
                if (wayOffset != null) {
                    drawWay(wayOffset, scale)
                }
                if (userPosition != null) {
                    drawUserPosition(userPosition, scale)
                }
            }
        }
        Column(
            Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
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
        }
    }
}

private fun DrawScope.drawWay(
    points: List<Offset>,
    scale: Float
) {
    if (points.size < 2) return

    // 1. Cấu hình giao diện (Style)
    val pathColor = Color(0xFF4285F4) // Xanh dương kiểu Google Maps

    // Tính toán độ dày nét vẽ dựa trên scale
    // Mục đích: Khi zoom map to lên, nét vẽ không bị "phình" ra, mà giữ nguyên độ mảnh
    val baseWidth = 4.dp.toPx()
    val adjustedStrokeWidth = (baseWidth / scale).coerceAtLeast(1f)

    // Tạo hiệu ứng nét đứt (Dashed Line)
    // Intervals cũng phải chia cho scale để độ dài vạch không bị phóng to
    val dashedEffect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(20f / scale, 10f / scale),
        phase = 0f
    )

    // 2. Tạo đối tượng Path từ List<Offset>
    val navigationPath = Path().apply {
        // Điểm bắt đầu (thường là User Position)
        moveTo(points.first().x, points.first().y)

        // Nối lần lượt qua các điểm trung gian đến đích
        // drop(1) để bỏ qua điểm đầu vì đã moveTo rồi
        points.drop(1).forEach { offset ->
            lineTo(offset.x, offset.y)
        }
    }

    // 3. Thực hiện vẽ đường đi
    drawPath(
        path = navigationPath,
        color = pathColor,
        style = Stroke(
            width = adjustedStrokeWidth,
            cap = StrokeCap.Round,  // Bo tròn đầu mút
            join = StrokeJoin.Round, // Bo tròn các khúc cua
            pathEffect = dashedEffect // Xóa dòng này nếu muốn vẽ nét liền
        )
    )

    // 4. (Tùy chọn) Vẽ điểm chốt ở đầu và cuối để map sinh động hơn
    val dotRadius = (5.dp.toPx() / scale)

    // Vẽ điểm đầu (Vị trí User)
    drawCircle(
        color = Color.Blue,
        radius = dotRadius,
        center = points.first()
    )

    // Vẽ điểm đích (Target)
    drawCircle(
        color = Color.Red,
        radius = dotRadius,
        center = points.last()
    )
}

private fun DrawScope.drawShapes(
    nodeWithShapes: List<NodeWithShape>,
    textPaint: Paint,
    draggingState: DraggingState?,
    selection: Selection,
    scale: Float
) {

    nodeWithShapes.forEach { node ->
        if (node.shape == null) return@forEach
        val w = node.shape.width
        val h = node.shape.height

        textPaint.textSize = 28f

        val isSelected =
            (selection as? Selection.NodeSelected)?.nodeWithShape?.node?.id == node.node.id
        val isSnapTarget = draggingState?.snapTargetNode?.id == node.node.id
        val halfW = w / 2
        val halfH = h / 2
        val topLeft = Offset(node.shape.centerX - halfW, node.shape.centerY - halfH)
        val size = Size(w, h)
        val center = Offset(node.shape.centerX, node.shape.centerY)

        val fillColor =
            if (isSnapTarget) Color(node.shape.color).copy(alpha = 0.8f) else Color(node.shape.color)
        val strokeColor = if (isSelected) Color.Red else Color.Black
        val strokeWidth = (if (isSelected) 6f else 2f) / scale
        val strokeStyle = Stroke(width = strokeWidth)

        // Vẽ thân hình (Map Shape)
        when (node.shape.shape) {
            Shape.Companion.ShapeType.RECTANGLE -> {
                val cornerRadius = CornerRadius(16f / scale, 16f / scale)
                drawRoundRect(
                    color = fillColor,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = strokeColor,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = cornerRadius,
                    style = strokeStyle
                )
            }
//                Shape.Companion.ShapeType.CAPSULE -> {
//                    val cornerRadius = CornerRadius(min(w, h) / 2, min(w, h) / 2)
//                    drawRoundRect(color = fillColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius)
//                    drawRoundRect(color = strokeColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius, style = strokeStyle)
//                }
            Shape.Companion.ShapeType.CIRCLE -> {
                drawOval(color = fillColor, topLeft = topLeft, size = size)
                drawOval(color = strokeColor, topLeft = topLeft, size = size, style = strokeStyle)
            }

            Shape.Companion.ShapeType.TRIANGLE, Shape.Companion.ShapeType.DIAMOND, Shape.Companion.ShapeType.PENTAGON, Shape.Companion.ShapeType.HEXAGON -> {
                val vertices = mutableListOf<Offset>()
                val radiusX = w / 2
                val radiusY = h / 2

                if (node.shape.shape == Shape.Companion.ShapeType.TRIANGLE) {
                    vertices.add(Offset(center.x, center.y - radiusY))
                    vertices.add(Offset(center.x + radiusX, center.y + radiusY))
                    vertices.add(Offset(center.x - radiusX, center.y + radiusY))
                } else if (node.shape.shape == Shape.Companion.ShapeType.DIAMOND) {
                    vertices.add(Offset(center.x, center.y - radiusY))
                    vertices.add(Offset(center.x + radiusX, center.y))
                    vertices.add(Offset(center.x, center.y + radiusY))
                    vertices.add(Offset(center.x - radiusX, center.y))
                } else {
                    val sides = if (node.shape.shape == Shape.Companion.ShapeType.PENTAGON) 5 else 6
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
                val path = Path()
                path.addRoundedPolygon(vertices, cornerFraction = 0.25f)
                drawPath(path = path, color = fillColor)
                drawPath(path = path, color = strokeColor, style = strokeStyle)
            }

            else -> {}
        }

        // Vẽ Text
        val fontHeight = textPaint.descent() - textPaint.ascent()
        val textOffset = fontHeight / 2 - textPaint.descent()
        drawContext.canvas.nativeCanvas.drawText(
            node.shape.label,
            center.x,
            center.y + textOffset,
            textPaint
        )

    }
}
