package ie.app.minimap.ui.screens.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.repository.EdgeRepository
import ie.app.minimap.data.local.repository.FloorConnectionRepository
import ie.app.minimap.data.local.repository.FloorRepository
import ie.app.minimap.data.local.repository.NodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import kotlin.math.pow

/**
 * Đại diện cho một nút trên biểu đồ.
 * @param id ID duy nhất.
 * @param label Tên nhãn (ví dụ: "Nút 1").
 * @param position Vị trí tâm (x, y).
 * @param radius Bán kính của nút.
 */
data class Node(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val position: Offset,
    val radius: Float = 60f
)

/**
 * Đại diện cho một cạnh nối hai nút.
 * @param id ID duy nhất.
 * @param startNodeId ID của nút bắt đầu.
 * @param endNodeId ID của nút kết thúc.
 */
data class Edge(
    val id: String = UUID.randomUUID().toString(),
    val startNodeId: String,
    val endNodeId: String
    // isSelected đã bị xoá khỏi đây
)

/**
 * Trạng thái khi đang kéo một cạnh mới.
 * @param startNode Nút bắt đầu kéo.
 * @param currentPosition Vị trí hiện tại của ngón tay.
 * @param snapTargetNode Nút mục tiêu có thể "dính" vào (nếu có).
 */
data class DraggingState(
    val startNode: Node,
    val currentPosition: Offset,
    val snapTargetNode: Node? = null
)

// Trạng thái lựa chọn
sealed class Selection {
    data object None : Selection()
    data class NodeSelected(val id: String) : Selection()
    data class EdgeSelected(val id: String) : Selection()
}

@HiltViewModel
class MapEditorViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val floorRepository: FloorRepository,
    private val edgeRepository: EdgeRepository,
    private val floorConnectionRepository: FloorConnectionRepository
) : ViewModel() {
    companion object {
        const val SNAP_THRESHOLD = 80f // Dính khi cách tâm nút 80px
        const val EDGE_TAP_THRESHOLD = 20f // Độ nhạy khi chạm vào cạnh
    }

    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<Edge>>(emptyList())
    val edges: StateFlow<List<Edge>> = _edges.asStateFlow()

    private val _draggingState = MutableStateFlow<DraggingState?>(null)
    val draggingState: StateFlow<DraggingState?> = _draggingState.asStateFlow()

    // Trạng thái Pan và Zoom
    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offset = MutableStateFlow(Offset.Zero)
    val offset: StateFlow<Offset> = _offset.asStateFlow()

    // Trạng thái để phân biệt Pan và Kéo-cạnh
    private val _isPanning = MutableStateFlow(false)

    // Trạng thái lựa chọn mới
    private val _selection = MutableStateFlow<Selection>(Selection.None)
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    // --- Quản lý Nút (Node) ---

    /** Thêm một nút mới tại vị trí chạm */
    fun addNode(position: Offset) {
        val newNode = Node(
            label = "Nút ${_nodes.value.size + 1}",
            position = position
        )
        _nodes.update { it + newNode }
    }

    /** Thêm một nút mới gần trung tâm màn hình (so với view hiện tại) */
    fun addNewNodeNearScreenCenter() {
        // Chúng ta không biết kích thước màn hình,
        // nên chỉ thêm ở vị trí (150, 150) so với góc nhìn hiện tại.
        val worldPosition = screenToWorld(Offset(150f, 150f))
        addNode(worldPosition)
    }

    /** Tìm nút theo ID */
    fun getNodeById(id: String): Node? = _nodes.value.find { it.id == id }

    // --- Quản lý Cạnh (Edge) ---

    /** Xoá lựa chọn hiện tại (nút hoặc cạnh) */
    fun deleteSelection() {
        when (val currentSelection = _selection.value) {
            is Selection.EdgeSelected -> {
                // Xoá cạnh
                _edges.update { currentEdges ->
                    currentEdges.filterNot { it.id == currentSelection.id }
                }
            }

            is Selection.NodeSelected -> {
                // Xoá nút
                _nodes.update { currentNodes ->
                    currentNodes.filterNot { it.id == currentSelection.id }
                }
                // Xoá các cạnh nối với nút đó
                _edges.update { currentEdges ->
                    currentEdges.filterNot {
                        it.startNodeId == currentSelection.id || it.endNodeId == currentSelection.id
                    }
                }
            }

            Selection.None -> {
                // Không làm gì
            }
        }
        // Bỏ chọn sau khi xoá
        _selection.value = Selection.None
    }

    /** Bỏ chọn tất cả các cạnh */
    private fun clearSelection() {
        _selection.value = Selection.None
    }

    // --- Xử lý cử chỉ (Gestures) ---

    /**
     * Chuyển đổi tọa độ Màn hình (Screen) sang tọa độ Thế giới (World)
     * (Tọa độ đã được pan/zoom)
     */
    private fun screenToWorld(screenPos: Offset): Offset {
        return (screenPos - _offset.value) / _scale.value
    }

    /**
     * Được gọi khi người dùng chạm (tap) vào canvas.
     * Dùng để chọn nút hoặc cạnh.
     */
    fun onTap(screenOffset: Offset) {
        val worldOffset = screenToWorld(screenOffset)

        // 1. Kiểm tra chạm vào nút
        val tappedNode = findTappedNode(worldOffset)
        if (tappedNode != null) {
            _selection.value = Selection.NodeSelected(tappedNode.id)
            return
        }

        // 2. Kiểm tra chạm vào cạnh
        val tappedEdge = findTappedEdge(worldOffset)
        if (tappedEdge != null) {
            _selection.value = Selection.EdgeSelected(tappedEdge.id)
            return
        }

        // 3. Bỏ chọn tất cả nếu chạm vào khoảng trống
        clearSelection()
    }

    /**
     * Được gọi khi người dùng bắt đầu kéo (drag).
     * Sẽ phân biệt giữa Pan (kéo nền) và Create Edge (kéo từ nút).
     */
    fun onDragStart(screenOffset: Offset) {
        val worldOffset = screenToWorld(screenOffset)
        val startNode = findTappedNode(worldOffset)

        if (startNode != null) {
            // Bắt đầu kéo để TẠO CẠNH
            _draggingState.value = DraggingState(startNode, worldOffset)
            clearSelection() // Bỏ chọn khi bắt đầu kéo cạnh mới
            _isPanning.value = false
        } else {
            // Bắt đầu PAN
            _isPanning.value = true
            clearSelection() // Bỏ chọn khi pan
        }
    }

    /**
     * Được gọi khi người dùng đang kéo.
     * Cập nhật vị trí đường kéo HOẶC cập nhật offset (pan).
     */
    fun onDragGesture(change: PointerInputChange, dragAmount: Offset) {
        if (_isPanning.value) {
            // Đang Pan
            _offset.update { it + dragAmount }
        } else if (_draggingState.value != null) {
            // Đang kéo cạnh
            onDragEdge(change.position)
        }
    }

    /**
     * Cập nhật vị trí đường kéo (logic onDrag cũ)
     */
    private fun onDragEdge(newScreenPosition: Offset) {
        val currentDrag = _draggingState.value ?: return
        val newWorldPosition = screenToWorld(newScreenPosition)

        // Tìm nút mục tiêu để "dính" vào
        val snapTarget = _nodes.value.find {
            it.id != currentDrag.startNode.id && // Không phải nút gốc
                    (it.position - newWorldPosition).getDistanceSquared() <= SNAP_THRESHOLD.pow(2)
        }

        _draggingState.value = currentDrag.copy(
            currentPosition = newWorldPosition,
            snapTargetNode = snapTarget
        )
    }

    /**
     * Được gọi khi người dùng nhả tay (kết thúc kéo).
     * Tạo cạnh mới HOẶC dừng pan.
     */
    fun onDragEnd() {
        if (_isPanning.value) {
            // Dừng pan
            _isPanning.value = false
        } else if (_draggingState.value != null) {
            // Hoàn tất kéo cạnh
            onDragEdgeEnd()
        }
    }

    /**
     * Tạo cạnh mới nếu đang "dính" vào một nút (logic onDragEnd cũ)
     */
    private fun onDragEdgeEnd() {
        val currentDrag = _draggingState.value ?: return
        val targetNode = currentDrag.snapTargetNode

        if (targetNode != null) {
            // Tạo cạnh mới nếu chưa tồn tại
            val edgeExists = _edges.value.any {
                (it.startNodeId == currentDrag.startNode.id && it.endNodeId == targetNode.id) ||
                        (it.startNodeId == targetNode.id && it.endNodeId == currentDrag.startNode.id)
            }
            if (!edgeExists) {
                _edges.update {
                    it + Edge(
                        startNodeId = currentDrag.startNode.id,
                        endNodeId = targetNode.id
                    )
                }
            }
        }
        // Hoàn tất kéo, xoá trạng thái
        _draggingState.value = null
    }

    /**
     * Được gọi khi người dùng Zoom (chỉ xử lý zoom)
     */
    fun onZoom(centroid: Offset, zoom: Float) {
        // Giới hạn tỷ lệ zoom
        val newScale = (_scale.value * zoom).coerceIn(0.1f, 5f)

        // Công thức chuẩn để zoom quanh 1 điểm (centroid)
        val zoomFactor = newScale / _scale.value
        _offset.update { (it - centroid) * zoomFactor + centroid }
        _scale.update { newScale }
    }

    /**
     * Phóng to/Thu nhỏ (dùng cho nút bấm)
     */
    fun zoom(factor: Float) {
        // Zoom đơn giản quanh gốc (0,0) của world
        _scale.update { (it * factor).coerceIn(0.1f, 5f) }
    }

    // --- Hàm trợ giúp tính toán ---

    private fun findTappedNode(offset: Offset): Node? {
        return _nodes.value.find {
            (it.position - offset).getDistanceSquared() <= it.radius.pow(2)
        }
    }

    private fun findTappedEdge(offset: Offset): Edge? {
        val tapThresholdSq = EDGE_TAP_THRESHOLD.pow(2)
        return _edges.value.find { edge ->
            val p1 = getNodeById(edge.startNodeId)?.position ?: return@find false
            val p2 = getNodeById(edge.endNodeId)?.position ?: return@find false
            distanceToSegmentSquared(offset, p1, p2) <= tapThresholdSq
        }
    }

    /**
     * Tính bình phương khoảng cách từ điểm p đến đoạn thẳng (v, w).
     * Dùng để kiểm tra va chạm với cạnh.
     */
    private fun distanceToSegmentSquared(p: Offset, v: Offset, w: Offset): Float {
        val l2 = (v - w).getDistanceSquared()
        if (l2 == 0f) return (p - v).getDistanceSquared() // Đoạn thẳng là 1 điểm
        val t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        val tClamped = t.coerceIn(0f, 1f)
        val projection = Offset(
            v.x + tClamped * (w.x - v.x),
            v.y + tClamped * (w.y - v.y)
        )
        return (p - projection).getDistanceSquared()
    }
}