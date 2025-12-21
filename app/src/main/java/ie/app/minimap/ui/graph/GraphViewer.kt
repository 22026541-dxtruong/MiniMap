package ie.app.minimap.ui.graph

import android.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
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
fun GraphViewer(
    floorId: Long = 1,
    userPosition: State<Offset?>,
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

    val visibleNodes by remember(nodes) {
        derivedStateOf { nodes.filter { it.node.type != Node.HALLWAY } }
    }

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
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .onSizeChanged { size -> viewModel.setScreenSize(size) }
                // --- BẮT ĐẦU VÙNG CACHE ---
                .drawWithCache {
                    val sharedPath = Path()
                    val sharedVertices = ArrayList<Offset>(6) // Đủ cho lục giác

                    onDrawBehind {
                        withTransform({
                            translate(left = offset.x, top = offset.y)
                            rotate(degrees = rotation, pivot = Offset.Zero)
                            scale(scale = scale, pivot = Offset.Zero)
                        }) {
                            // Truyền các object dùng chung vào hàm vẽ
                            drawShapes(
                                nodeWithShapes = nodes,
                                textPaint = textPaint,
                                draggingState = draggingState,
                                selection = selection,
                                scale = scale,
                                reusablePath = sharedPath,       // <---
                                reusableVertices = sharedVertices // <---
                            )

                            // Vẽ Nodes (Dùng list đã lọc visibleNodes)
                            drawNodes(visibleNodes, draggingState, selection, scale)

                            if (wayOffset != null) {
                                drawWay(wayOffset, scale, sharedPath) // <--- Tái sử dụng path
                            }
                            if (userPosition.value != null) {
                                drawUserPosition(userPosition.value, scale)
                            }
                        }
                    }
                }
                // --- CÁC GESTURE GIỮ NGUYÊN ---
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        viewModel.onTransform(centroid, pan, zoom, rotation)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.onTap(it) })
                }
        )
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
            userPosition.value?.let {
                FilledTonalIconButton(
                    onClick = { viewModel.centerOnNode(userPosition.value!!) }
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
    scale: Float,
    reusablePath: Path
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
//    val navigationPath = Path().apply {
//        // Điểm bắt đầu (thường là User Position)
//        moveTo(points.first().x, points.first().y)
//
//        // Nối lần lượt qua các điểm trung gian đến đích
//        // drop(1) để bỏ qua điểm đầu vì đã moveTo rồi
//        points.drop(1).forEach { offset ->
//            lineTo(offset.x, offset.y)
//        }
//    }
    reusablePath.reset()
    reusablePath.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) { // Dùng vòng lặp for i thay vì drop(1) để tránh tạo list phụ
        reusablePath.lineTo(points[i].x, points[i].y)
    }

    // 3. Thực hiện vẽ đường đi
    drawPath(
        path = reusablePath,
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
    scale: Float,
    reusablePath: Path,          // <--- Input
    reusableVertices: ArrayList<Offset> // <--- Input
) {
    nodeWithShapes.forEach { node ->
        val shape = node.shape ?: return@forEach
        val w = shape.width
        val h = shape.height

        val isSelected = (selection as? Selection.NodeSelected)?.nodeWithShape?.node?.id == node.node.id
        val isSnapTarget = draggingState?.snapTargetNode?.id == node.node.id

        val cx = shape.centerX
        val cy = shape.centerY
        val size = Size(w, h)
        val topLeft = Offset(cx - w/2, cy - h/2)

        val fillColor = if (isSnapTarget) Color(shape.color).copy(alpha = 0.8f) else Color(shape.color)
        val strokeColor = if (isSelected) Color.Red else Color.Black
        val strokeWidth = (if (isSelected) 6f else 2f) / scale
        val strokeStyle = Stroke(width = strokeWidth)

        // Reset path cho vòng lặp này
        reusablePath.reset()

        when (shape.shape) {
            Shape.Companion.ShapeType.RECTANGLE -> {
                val cornerRadius = CornerRadius(16f / scale, 16f / scale)
                drawRoundRect(color = fillColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius)
                drawRoundRect(color = strokeColor, topLeft = topLeft, size = size, cornerRadius = cornerRadius, style = strokeStyle)
            }
            Shape.Companion.ShapeType.CIRCLE -> {
                drawOval(color = fillColor, topLeft = topLeft, size = size)
                drawOval(color = strokeColor, topLeft = topLeft, size = size, style = strokeStyle)
            }
            // Các hình đa giác phức tạp
            else -> {
                reusableVertices.clear() // Xóa dữ liệu đỉnh cũ
                val radiusX = w / 2
                val radiusY = h / 2

                if (shape.shape == Shape.Companion.ShapeType.TRIANGLE) {
                    reusableVertices.add(Offset(cx, cy - radiusY))
                    reusableVertices.add(Offset(cx + radiusX, cy + radiusY))
                    reusableVertices.add(Offset(cx - radiusX, cy + radiusY))
                } else if (shape.shape == Shape.Companion.ShapeType.DIAMOND) {
                    reusableVertices.add(Offset(cx, cy - radiusY))
                    reusableVertices.add(Offset(cx + radiusX, cy))
                    reusableVertices.add(Offset(cx, cy + radiusY))
                    reusableVertices.add(Offset(cx - radiusX, cy))
                } else {
                    val sides = if (shape.shape == Shape.Companion.ShapeType.PENTAGON) 5 else 6
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

                // Hàm này phải được cập nhật để dùng reusablePath thay vì tạo path mới
                // Bạn có thể viết logic addPoly vào reusablePath ngay tại đây hoặc sửa hàm extension
                reusablePath.addRoundedPolygon(reusableVertices, 0.25f)

                drawPath(path = reusablePath, color = fillColor)
                drawPath(path = reusablePath, color = strokeColor, style = strokeStyle)
            }
        }

        // Vẽ Text
        val fontHeight = textPaint.descent() - textPaint.ascent()
        val textOffset = fontHeight / 2 - textPaint.descent()
        drawContext.canvas.nativeCanvas.drawText(
            shape.label,
            cx,
            cy + textOffset,
            textPaint
        )
    }
}
