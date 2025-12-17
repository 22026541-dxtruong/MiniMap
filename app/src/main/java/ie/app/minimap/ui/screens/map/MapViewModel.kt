package ie.app.minimap.ui.screens.map

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.relations.BoothWithVendor
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.data.local.repository.InfoRepository
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
import java.util.PriorityQueue
import javax.inject.Inject
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.math.hypot

data class MapEditorUiState(
    val selectedBuilding: Building = Building(),
    val selectedFloor: Floor = Floor(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapEditorUiState(isLoading = true))
    val uiState: StateFlow<MapEditorUiState> = _uiState.asStateFlow()
    private val _buildings = MutableStateFlow<List<Building>>(emptyList())
    val buildings: StateFlow<List<Building>> = _buildings.asStateFlow()
    private val _floors = MutableStateFlow<List<Floor>>(emptyList())
    val floors: StateFlow<List<Floor>> = _floors.asStateFlow()
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes.asStateFlow()
    private val _edges = MutableStateFlow<List<Edge>>(emptyList())
    val edges: StateFlow<List<Edge>> = _edges.asStateFlow()
    private val _userPosition = MutableStateFlow<Offset?>(null)
    val userPosition: StateFlow<Offset?> = _userPosition.asStateFlow()
    private val _searchResult = MutableStateFlow<List<NodeWithShape>>(emptyList())
    val searchResult: StateFlow<List<NodeWithShape>> = _searchResult.asStateFlow()
    private val _boothWithVendor = MutableStateFlow<BoothWithVendor?>(null)
    val boothWithVendor = _boothWithVendor.asStateFlow()

    private val _pathOffsetAndNode = MutableStateFlow<Pair<List<Offset>, List<Node>>?>(null)
    val pathOffsetAndNode: StateFlow<Pair<List<Offset>, List<Node>>?> = _pathOffsetAndNode.asStateFlow()

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
            mapRepository.getFloorsByBuildingId(buildingId)
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
                    _nodes.value = floorGraph.nodeWithShapes
                        .filter { (node, _) -> node.type != Node.HALLWAY }
                        .map { it.node }
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
        setupDependentFlows()
    }

    fun onFloorSelected(floor: Floor) {
        _uiState.update { it.copy(selectedFloor = floor) }
    }

    fun onBuildingSelected(building: Building) {
        _uiState.update { it.copy(selectedBuilding = building) }
    }

    fun updateUserLocation(offset: Offset?) {
        _userPosition.value = offset
    }

    fun getNodesByLabel(label: String) {
        viewModelScope.launch {
            infoRepository.getShapesByLabel(label, _uiState.value.selectedFloor.id).collect {
                _searchResult.value = it.filter { nodeWithShape -> nodeWithShape.shape != null }
            }
        }
    }

    fun loadBoothWithVendor(nodeId: Long) {
        viewModelScope.launch {
            _boothWithVendor.value =
                infoRepository.getBoothWithVendorByNodeId(nodeId)
        }
    }


    fun findPathToNode(targetNode: Node) {
        val currentUserPos = _userPosition.value ?: return
        val allNodes = _nodes.value
        val allEdges = _edges.value

        // --- BƯỚC 1: TÌM ĐIỂM VÀO (START NODE) ---
        // Lấy tất cả các node ĐANG tham gia vào mạng lưới (có ít nhất 1 cạnh nối)
        val connectedNodeIds = allEdges.flatMap { listOf(it.fromNode, it.toNode) }.toSet()

        // Tìm node trong mạng lưới gần người dùng nhất
        val startNode = allNodes
            .filter { it.id in connectedNodeIds }
            .minByOrNull { calculateDistance(currentUserPos, it) }
        // Nếu không có node nào trong mạng lưới, trả về đường thẳng trực tiếp
            ?: return

        // --- BƯỚC 2: TÌM ĐIỂM RA (END GATEWAY) ---
        // Nếu targetNode đã nằm trong mạng lưới rồi thì nó chính là đích
        // Nếu targetNode bị cô lập (không có cạnh), ta tìm node trong mạng lưới gần nó nhất để làm "cửa ngõ"
        val endGatewayNode = if (targetNode.id in connectedNodeIds) {
            targetNode
        } else {
            allNodes
                .filter { it.id in connectedNodeIds && it.id != targetNode.id } // Trừ chính nó ra
                .minByOrNull {
                    calculateDistance(
                        Offset(
                            targetNode.x,
                            targetNode.y
                        ), it
                    )
                }
                ?: startNode // Fallback: nếu ko tìm được thì quay về start
        }

        // --- BƯỚC 3: DIJKSTRA (VÔ HƯỚNG) ---
        val pathNodes =
            runDijkstra(startNode.id, endGatewayNode.id, allNodes, allEdges)

        // --- BƯỚC 4: TỔNG HỢP ĐƯỜNG ĐI (OFFSET) ---
        val pathOffsets = mutableListOf<Offset>()

        // 4a. Từ vị trí thực của User -> Node bắt đầu (Start Node)
        pathOffsets.add(currentUserPos)

        // 4b. Các node trong đồ thị
        pathOffsets.addAll(pathNodes.map { Offset(it.x, it.y) })

        // 4c. Từ Node cửa ngõ (Gateway) -> Vị trí Node đích thực sự (nếu khác nhau)
        // Đây là đoạn "đi bộ" từ mạng lưới đến cái ghế/bàn cụ thể
        if (endGatewayNode.id != targetNode.id) {
            pathOffsets.add(Offset(targetNode.x, targetNode.y))
        } else if (pathOffsets.isEmpty()) {
            // Trường hợp User đang đứng ngay tại đích hoặc rất gần
            pathOffsets.add(Offset(targetNode.x, targetNode.y))
        }

        _pathOffsetAndNode.value = Pair(pathOffsets, pathNodes)
    }

    fun clearPath() {
        _pathOffsetAndNode.value = null
    }

    private fun runDijkstra(
        startId: Long,
        endId: Long,
        nodes: List<Node>,
        edges: List<Edge>
    ): List<Node> {
        if (startId == endId) return listOfNotNull(nodes.find { it.id == startId })

        // Xây dựng đồ thị VÔ HƯỚNG
        val adj = HashMap<Long, MutableList<Pair<Long, Float>>>()
        edges.forEach { edge ->
            val w = if (edge.weight > 0) edge.weight else 1f
            // Add cả 2 chiều để đảm bảo vô hướng
            adj.computeIfAbsent(edge.fromNode) { ArrayList() }.add(edge.toNode to w)
            adj.computeIfAbsent(edge.toNode) { ArrayList() }.add(edge.fromNode to w)
        }

        val distances = HashMap<Long, Float>().withDefault { Float.MAX_VALUE }
        val previous = HashMap<Long, Long>()
        val priorityQueue = PriorityQueue<PathNode>()

        nodes.forEach { distances[it.id] = Float.MAX_VALUE }
        distances[startId] = 0f
        priorityQueue.add(PathNode(startId, 0f))

        while (priorityQueue.isNotEmpty()) {
            val (u, distU) = priorityQueue.poll() ?: break

            // Tối ưu: Nếu khoảng cách lấy ra lớn hơn khoảng cách đã lưu -> bỏ qua
            if (distU > (distances[u] ?: Float.MAX_VALUE)) continue
            if (u == endId) break

            adj[u]?.forEach { (v, weight) ->
                val newDist = distU + weight
                if (newDist < (distances[v] ?: Float.MAX_VALUE)) {
                    distances[v] = newDist
                    previous[v] = u
                    priorityQueue.add(PathNode(v, newDist))
                }
            }
        }

        // Truy vết ngược (Backtracking)
        val path = mutableListOf<Node>()
        var curr: Long? = endId

        // Kiểm tra tính liên thông: Nếu endId chưa bao giờ được update previous (trừ khi start==end), nghĩa là ko có đường
        if (previous[endId] == null && startId != endId) return emptyList()

        while (curr != null) {
            val node = nodes.find { it.id == curr }
            if (node != null) path.add(node)
            curr = previous[curr]
        }

        return path.reversed()
    }

    // Helper: Tính khoảng cách giữa User (Offset) và Node
    private fun calculateDistance(pos: Offset, node: Node): Float {
        return hypot(pos.x - node.x, pos.y - node.y)
    }
}

data class PathNode(val nodeId: Long, val distance: Float) : Comparable<PathNode> {
    override fun compareTo(other: PathNode): Int = this.distance.compareTo(other.distance)
}
