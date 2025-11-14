package ie.app.minimap.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ie.app.minimap.ui.screens.editor.DraggingState
import ie.app.minimap.ui.screens.editor.Edge
import ie.app.minimap.ui.screens.editor.MapEditorViewModel
import ie.app.minimap.ui.screens.editor.Node
import ie.app.minimap.ui.screens.editor.Selection

@Composable
fun MapEditor(
    nodes: List<Node>,
    edges: List<Edge>,
    draggingState: DraggingState?,
    scale: Float,
    offset: Offset,
    selection: Selection,

    // callback gửi ngược ra ngoài
    addNewNodeNearScreenCenter: () -> Unit,
    onZoom: (centroid: Offset, zoom: Float) -> Unit,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    onZoomButton: (Float) -> Unit,
) {
    val textPaint = remember {
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
    }

    Column(Modifier.fillMaxSize()) {

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = { addNewNodeNearScreenCenter() }) {
                Text("Them Node")
            }

            Button(onClick = { onZoomButton(1.2f) }) {
                Text("Phóng To")
            }
            Button(onClick = { onZoomButton(0.8f) }) {
                Text("Thu Nhỏ")
            }

            if (selection != Selection.None) {
                Button(onClick = onDelete) {
                    Text(
                        when (selection) {
                            is Selection.NodeSelected -> "Xoá Nút"
                            is Selection.EdgeSelected -> "Xoá Cạnh"
                            else -> ""
                        }
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF0F0F0))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onZoom(centroid, zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { onTap(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = onDragStart,
                        onDrag = { change, dragAmount ->
                            onDrag(change, dragAmount)
                            change.consume()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
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
        val startNode = nodes.find { it.id == edge.startNodeId }
        val endNode = nodes.find { it.id == edge.endNodeId }

        if (startNode != null && endNode != null) {
            val isSelected = (selection as? Selection.EdgeSelected)?.id == edge.id

            drawLine(
                color = if (isSelected) Color.Red else Color.Black,
                start = startNode.position,
                end = endNode.position,
                strokeWidth = if (isSelected) 10f else 5f
            )
        }
    }
}

private fun DrawScope.drawDraggingLine(draggingState: DraggingState?) {
    draggingState?.let { drag ->
        val startPos = drag.startNode.position
        // Nếu "dính" thì vẽ đến tâm nút, nếu không thì vẽ đến ngón tay
        val endPos = drag.snapTargetNode?.position ?: drag.currentPosition

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
            center = node.position,
            radius = node.radius
        )

        // Vẽ viền
        drawCircle(
            color = if (isSelected) Color.Red else Color.Black, // Màu viền đỏ nếu được chọn
            center = node.position,
            radius = node.radius,
            style = Stroke(width = if (isSelected) 8f else 3f) // Viền dày hơn nếu được chọn
        )

        // Vẽ nhãn (label)
        drawContext.canvas.nativeCanvas.drawText(
            node.label,
            node.position.x,
            node.position.y - (textPaint.descent() + textPaint.ascent()) / 2, // Căn giữa
            textPaint
        )

        // Vẽ vòng "dính" (snap ring) nếu nút này là mục tiêu
        if (draggingState?.snapTargetNode?.id == node.id) {
            drawCircle(
                color = Color.Green.copy(alpha = 0.5f),
                center = node.position,
                radius = MapEditorViewModel.SNAP_THRESHOLD,
                style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
            )
        }
    }
}