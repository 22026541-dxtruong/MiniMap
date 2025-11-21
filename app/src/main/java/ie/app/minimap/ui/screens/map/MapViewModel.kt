package ie.app.minimap.ui.screens.map

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

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
    data class NodeSelected(val id: Long) : Selection()
    data class EdgeSelected(val id: Long) : Selection()
}

data class MapUiState(
    val selectedBuilding: Building = Building(),
    val selectedFloor: Floor = Floor(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapRepository: MapRepository
) : ViewModel() {
    companion object {
        const val SNAP_THRESHOLD = 80f // Dính khi cách tâm nút 80px
        const val EDGE_TAP_THRESHOLD = 20f // Độ nhạy khi chạm vào cạnh
        const val RADIUS = 60f // Bán kính nút
    }

    private val _uiState = MutableStateFlow(MapUiState(isLoading = true))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _userPosition = MutableStateFlow<Offset?>(null)
    val userPosition: StateFlow<Offset?> = _userPosition.asStateFlow()

    private val _buildings = MutableStateFlow<List<Building>>(emptyList())
    val buildings: StateFlow<List<Building>> = _buildings.asStateFlow()
    private val _floors = MutableStateFlow<List<Floor>>(emptyList())
    val floors: StateFlow<List<Floor>> = _floors.asStateFlow()

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

    private val _rotation = MutableStateFlow(0f)
    val rotation: StateFlow<Float> = _rotation.asStateFlow()

    // Trạng thái để phân biệt Pan và Kéo-cạnh
    private val _isPanning = MutableStateFlow(false)

    // Trạng thái lựa chọn mới
    private val _selection = MutableStateFlow<Selection>(Selection.None)
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    // Theo dõi kích thước màn hình để căn giữa
    private var _viewSize = IntSize.Zero
    private var _isInitialized = false

    // --- Quản lý Khởi tạo ---

    fun setScreenSize(size: IntSize) {
        if (_viewSize == size) return
        _viewSize = size

        // Chỉ set offset về tâm trong lần đầu tiên (khi app vừa mở)
        if (!_isInitialized && size.width > 0 && size.height > 0) {
            _offset.value = Offset(size.width / 2f, size.height / 2f)
            _isInitialized = true
        }
    }

    private val _selectedBuildingIdFlow: Flow<Long?> = _uiState
        .map { it.selectedBuilding.id }
        .distinctUntilChanged() // Chỉ phát ra khi ID thực sự thay đổi
        .filter { it > 0L }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _floorsFlow: Flow<List<Floor>> = _selectedBuildingIdFlow
        .filterNotNull()
        .flatMapLatest { buildingId ->
            Log.d("MapEditorViewModel", "Building ID: $buildingId")
            // Tải BuildingWithFloors và chỉ lấy ra danh sách Floors
            mapRepository.getBuildingWithFloorsByBuildingId(buildingId)
                .map { it.floors }
        }
        .onEach { floors ->
            // Cập nhật StateFlow _floors và chọn Floor đầu tiên
            Log.d("MapEditorViewModel", "Floors: $floors")
            _floors.value = floors

            // Cập nhật SelectedFloor mới
            _uiState.update { it.copy(selectedFloor = floors.first()) }
        }
        .catch { // Xử lý lỗi tải Floors nếu cần
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupDependentFlows() {
        // 1. Lắng nghe sự thay đổi của Building để tải Floors
        viewModelScope.launch {
            _floorsFlow.collect() // Khởi động việc lắng nghe Floors (như ở Bước 2)
        }

        // 2. Lắng nghe sự thay đổi của Floor ID
        val selectedFloorIdFlow: Flow<Long?> = _uiState
            .map { it.selectedFloor.id }
            .distinctUntilChanged()
            .filter { id -> id > 0L } // ✨ Bổ sung: Chỉ cho phép ID > 0 ✨
            .map { it }

        viewModelScope.launch {
            selectedFloorIdFlow
                .filterNotNull()
                .flatMapLatest { floorId ->
                    Log.d("MapEditorViewModel", "setupDependentFlows: $floorId")
                    // Tải Nodes và Edges cho Floor mới được chọn
                    mapRepository.getFloorWithNodesAndEdgeByFloorId(floorId)

                }
                .collect { floorGraph ->
                    Log.d("MapEditorViewModel", "setupDependentFlows: ${floorGraph.toString()}")
                    // Cập nhật Nodes và Edges mới
                    _nodes.value = floorGraph.nodes
                    _edges.value = floorGraph.edges
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadMap(venueId: Long) {
        viewModelScope.launch {
            mapRepository.getAllBuildingsByVenueId(venueId)
                .collect { buildings ->
                    // Cập nhật danh sách Buildings
                    _buildings.value = buildings

                    // Chỉ thiết lập Building và Floor nếu chúng chưa được chọn
                    _uiState.update { it.copy(selectedBuilding = buildings.first()) }

                    _uiState.update { it.copy(isLoading = false) }
                }
        }
        // Gọi hàm thiết lập Flow phụ thuộc (Xem Bước 3)
        setupDependentFlows()
    }

    fun onFloorSelected(floor: Floor) {
        _uiState.update { it.copy(selectedFloor = floor) }
    }

    fun onBuildingSelected(building: Building) {
        _uiState.update { it.copy(selectedBuilding = building) }
    }

    fun updateUserLocation(x: Float, y: Float) {
        _userPosition.value = Offset(x, y)
    }

    // --- Quản lý Nút (Node) ---

    /** Thêm một nút mới tại vị trí chạm */
    fun addNode(position: Offset) {
        viewModelScope.launch {
            mapRepository.upsertNode(
                Node(
                    floorId = _uiState.value.selectedFloor.id,
                    x = position.x,
                    y = position.y,
                    label = "Nút ${_nodes.value.size + 1}"
                )
            )
        }
//        _nodes.update { it + newNode }
    }

    /** Thêm một nút mới gần trung tâm màn hình (so với view hiện tại) */
    fun addNewNodeNearScreenCenter() {
        // Chúng ta không biết kích thước màn hình,
        // nên chỉ thêm ở vị trí (150, 150) so với góc nhìn hiện tại.
        val worldPosition = screenToWorld(Offset(150f, 150f))
        addNode(worldPosition)
    }

    /** Tìm nút theo ID */
    fun getNodeById(id: Long): Node? = _nodes.value.find { it.id == id }

    // --- Quản lý Cạnh (Edge) ---

    /** Xoá lựa chọn hiện tại (nút hoặc cạnh) */
    fun deleteSelection() {
        when (val currentSelection = _selection.value) {
            is Selection.EdgeSelected -> {
                // Xoá cạnh
//                _edges.update { currentEdges ->
//                    currentEdges.filterNot { it.id == currentSelection.id }
//                }
                viewModelScope.launch {
                    mapRepository.deleteEdge(Edge(id = currentSelection.id))
                }
            }

            is Selection.NodeSelected -> {
                // Xoá nút
//                _nodes.update { currentNodes ->
//                    currentNodes.filterNot { it.id == currentSelection.id }
//                }
                // Xoá các cạnh nối với nút đó
//                _edges.update { currentEdges ->
//                    currentEdges.filterNot {
//                        it.startNodeId == currentSelection.id || it.endNodeId == currentSelection.id
//                    }
//                }
                viewModelScope.launch {
                    mapRepository.deleteNode(Node(id = currentSelection.id))
                    mapRepository.deleteEdgesByNodeId(currentSelection.id)
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
     * Hàm tiện ích để xoay một điểm quanh gốc (0,0)
     */
    private fun rotatePoint(point: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians)
        val sin = sin(radians)
        val newX = point.x * cos - point.y * sin
        val newY = point.x * sin + point.y * cos
        return Offset(newX.toFloat(), newY.toFloat())
    }

    /**
     * Chuyển đổi Screen -> World với Offset, Scale và Rotation
     * Công thức: World = Rotate_Inverse(Screen - Offset) / Scale
     */
    private fun screenToWorld(screenPos: Offset): Offset {
        val translated = screenPos - _offset.value
        // Xoay ngược lại để tìm tọa độ gốc
        val rotated = rotatePoint(translated, -_rotation.value)
        return rotated / _scale.value
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
                    (Offset(it.x, it.y) - newWorldPosition).getDistanceSquared() <= SNAP_THRESHOLD.pow(2)
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
                (it.fromNode == currentDrag.startNode.id && it.toNode == targetNode.id) ||
                        (it.fromNode == targetNode.id && it.toNode == currentDrag.startNode.id)
            }
            if (!edgeExists) {
                viewModelScope.launch {
                    mapRepository.upsertEdge(
                        Edge(
                            fromNode = currentDrag.startNode.id,
                            toNode = targetNode.id,
                            floorId = _uiState.value.selectedFloor.id
                        )
                    )
                }
//                _edges.update {
//                    it + Edge(
//                        startNodeId = currentDrag.startNode.id,
//                        endNodeId = targetNode.id
//                    )
//                }
            }
        }
        // Hoàn tất kéo, xoá trạng thái
        _draggingState.value = null
    }

    /**
     * Xử lý Zoom, Pan (2 ngón) và Rotate
     */
    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, rotationChange: Float) {
        // 1. Xác định điểm trên bản đồ nằm dưới tay ở TRẠNG THÁI CŨ
        // centroid: vị trí hiện tại của tay
        // pan: độ dịch chuyển của tay so với lần trước
        // => centroid - pan = vị trí cũ của tay
        val oldScreenPos = centroid - pan
        val pivotWorldPos = screenToWorld(oldScreenPos) // Điểm neo trên bản đồ

        // 2. Cập nhật Scale và Rotation mới
        val newScale = (_scale.value * zoom).coerceIn(0.1f, 10f)
        val newRotation = _rotation.value + rotationChange

        _scale.value = newScale
        _rotation.value = newRotation

        // 3. Tính toán Offset mới
        // Mục tiêu: pivotWorldPos (sau khi scale & rotate) phải nằm đúng tại vị trí centroid (vị trí tay hiện tại)

        // Tọa độ vector của điểm neo đã biến đổi (tính từ gốc 0,0 của World)
        val scaledWorldVector = pivotWorldPos * newScale
        val rotatedScaledWorldVector = rotatePoint(scaledWorldVector, newRotation)

        // Offset = Vị trí tay hiện tại - Vector điểm neo đã biến đổi
        _offset.value = centroid - rotatedScaledWorldVector
    }

    /**
     * Phóng to/Thu nhỏ (dùng cho nút bấm)
     */
    fun zoom(factor: Float) {
        // Zoom quanh tâm màn hình hiện tại
        val centerX = _viewSize.width / 2f
        val centerY = _viewSize.height / 2f
        val center = Offset(centerX, centerY)

        // Sử dụng logic onTransform để zoom mượt mà quanh tâm
        // Giả lập zoom 1.2x hoặc 0.8x
        onTransform(centroid = center, pan = Offset.Zero, zoom = factor, rotationChange = 0f)
    }

    // --- Hàm trợ giúp tính toán ---

    private fun findTappedNode(offset: Offset): Node? {
        return _nodes.value.find {
            (Offset(it.x, it.y) - offset).getDistanceSquared() <= RADIUS.pow(2)
        }
    }

    private fun findTappedEdge(offset: Offset): Edge? {
        val tapThresholdSq = EDGE_TAP_THRESHOLD.pow(2)
        return _edges.value.find { edge ->
            val fromNode = getNodeById(edge.fromNode) ?: return@find false
            val p1 = Offset(fromNode.x, fromNode.y)
            val toNode = getNodeById(edge.toNode) ?: return@find false
            val p2 = Offset(toNode.x, toNode.y)
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
