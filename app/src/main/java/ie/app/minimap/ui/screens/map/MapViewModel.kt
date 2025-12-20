package ie.app.minimap.ui.screens.map

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.relations.BoothWithVendor
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import kotlin.math.hypot

data class MapEditorUiState(
    val selectedBuilding: Building = Building(),
    val selectedFloor: Floor = Floor(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SearchResult(
    val floor: Floor,
    val building: Building,
    val node: NodeWithShape
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
    private val _searchResult = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResult: StateFlow<List<SearchResult>> = _searchResult.asStateFlow()
    private val _boothWithVendor = MutableStateFlow<BoothWithVendor?>(null)
    val boothWithVendor = _boothWithVendor.asStateFlow()

    private val _allVendors = MutableStateFlow<List<Vendor>>(emptyList())
    val allVendors: StateFlow<List<Vendor>> = _allVendors.asStateFlow()

    private val _pathOffsetAndNode = MutableStateFlow<Pair<List<Offset>, List<Node>>?>(null)
    val pathOffsetAndNode: StateFlow<Pair<List<Offset>, List<Node>>?> = _pathOffsetAndNode.asStateFlow()

    private val _selectedBuildingIdFlow: Flow<Long?> = _uiState
        .map { it.selectedBuilding.id }
        .distinctUntilChanged()
        .filter { it > 0L }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _floorsFlow: Flow<List<Floor>> = _selectedBuildingIdFlow
        .filterNotNull()
        .flatMapLatest { buildingId ->
            mapRepository.getFloorsByBuildingId(buildingId)
        }
        .onEach { floors ->
            _floors.value = floors
            if (floors.isNotEmpty()) {
                val currentFloorId = _uiState.value.selectedFloor.id
                if (floors.none { it.id == currentFloorId }) {
                    _uiState.update { it.copy(selectedFloor = floors.first()) }
                }
            } else {
                _uiState.update { it.copy(selectedFloor = Floor()) }
            }
        }

    init {
        viewModelScope.launch {
            infoRepository.getAllVendors().collect {
                _allVendors.value = it
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupDependentFlows() {
        viewModelScope.launch {
            _floorsFlow.collect()
        }

        val selectedFloorIdFlow: Flow<Long?> = _uiState
            .map { it.selectedFloor.id }
            .distinctUntilChanged()
            .filter { id -> id > 0L }

        viewModelScope.launch {
            selectedFloorIdFlow
                .filterNotNull()
                .flatMapLatest { floorId ->
                    mapRepository.getFloorWithNodesAndEdgeByFloorId(floorId)
                }
                .collect { floorGraph ->
                    if (floorGraph != null) {
                        _nodes.value = floorGraph.nodeWithShapes
//                            .filter { (node, _) -> node.type != Node.HALLWAY }
                            .map { it.node }
                        _edges.value = floorGraph.edges
                    } else {
                        _nodes.value = emptyList()
                        _edges.value = emptyList()
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadMap(venueId: Long) {
        viewModelScope.launch {
            mapRepository.getAllBuildingsByVenueId(venueId)
                .collect { buildings ->
                    _buildings.value = buildings
                    if (buildings.isNotEmpty()) {
                        val currentBuildingId = _uiState.value.selectedBuilding.id
                        if (currentBuildingId == 0L || buildings.none { it.id == currentBuildingId }) {
                            _uiState.update { it.copy(selectedBuilding = buildings.first()) }
                        }
                    }
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

    fun upsertBuilding(building: Building) {
        viewModelScope.launch {
            mapRepository.upsertBuilding(building)
            onBuildingSelected(building)
        }
    }

    fun deleteBuilding(building: Building) {
        viewModelScope.launch {
            val currentBuildings = _buildings.value
            val index = currentBuildings.indexOfFirst { it.id == building.id }
            if (index != -1 && currentBuildings.size > 1) {
                val newSelection = if (index > 0) currentBuildings[index - 1] else currentBuildings[1]
                onBuildingSelected(newSelection)
            }
            mapRepository.deleteBuilding(building)
        }
    }

    fun upsertFloor(floor: Floor) {
        viewModelScope.launch {
            mapRepository.upsertFloor(floor)
            onFloorSelected(floor)
        }
    }

    fun deleteFloor(floor: Floor) {
        viewModelScope.launch {
            val currentFloors = _floors.value
            val index = currentFloors.indexOfFirst { it.id == floor.id }
            if (index != -1 && currentFloors.size > 1) {
                val newSelection = if (index > 0) currentFloors[index - 1] else currentFloors[1]
                onFloorSelected(newSelection)
            }
            mapRepository.deleteFloor(floor)
        }
    }

    fun updateUserLocation(offset: Offset?) {
        _userPosition.value = offset
    }

    fun getNodesByLabel(label: String) {
        viewModelScope.launch {
            infoRepository.getShapesByLabel(label, _uiState.value.selectedFloor.id).collect { nodes ->
                _searchResult.value = nodes.filter { nodeWithShape -> nodeWithShape.shape != null }
                    .map {
                        val floorResult = _floors.value.first { floor -> floor.id == it.node.floorId }
                        SearchResult(
                            floorResult,
                            _buildings.value.first { building -> building.id == floorResult.buildingId },
                            it
                        )
                    }
            }
        }
    }

    fun loadBoothWithVendor(nodeId: Long) {
        viewModelScope.launch {
            _boothWithVendor.value =
                infoRepository.getBoothWithVendorByNodeId(nodeId)
        }
    }

    fun upsertBoothWithVendor(booth: Booth, vendor: Vendor) {
        viewModelScope.launch {
            infoRepository.updateBoothAndVendor(booth, vendor)
            // Reload after update
            loadBoothWithVendor(booth.nodeId)
        }
    }

    fun updateNodeLabel(nodeWithShape: NodeWithShape, label: String) {
        viewModelScope.launch {
            nodeWithShape.shape?.let {
                mapRepository.upsertShape(it.copy(label = label))
            }
        }
    }

    // 1. Hàm tìm đường chính (đã tối ưu)
    fun findPathToNode(targetNode: Node) {
        val currentUserPos = _userPosition.value ?: return
        val allNodes = _nodes.value.filter { it.type != Node.HALLWAY }
        val allEdges = _edges.value

        val connectedNodeIds = allEdges.flatMap { listOf(it.fromNode, it.toNode) }.toSet()

        // Xử lý node đích (nếu đích không nằm trên đường đi thì tìm điểm kết nối gần nhất)
        val endGatewayNode = if (targetNode.id in connectedNodeIds) {
            targetNode
        } else {
            allNodes
                .filter { it.id in connectedNodeIds && it.id != targetNode.id }
                .minByOrNull { calculateDistance(Offset(targetNode.x, targetNode.y), it) }
                ?: return
        }

        // --- BƯỚC 1: Chạy Dijkstra TỪ ĐÍCH ra toàn bản đồ ---
        // Kết quả trả về map chứa node trước đó (previous) và khoảng cách (distances)
        val dijkstraResult = runDijkstraFullMap(endGatewayNode.id, allNodes, allEdges)
        val distancesToTarget = dijkstraResult.distances
        val pathTree = dijkstraResult.previous

        // --- BƯỚC 2: Tìm điểm bắt đầu tối ưu nhất ---
        // Duyệt qua các node có thể kết nối, tìm node mà: (User đi bộ đến đó + Từ đó về đích) là nhỏ nhất
        val bestStartNode = allNodes
            .filter { it.id in connectedNodeIds } // Chỉ xét node có kết nối
            // Lọc sơ bộ: Chỉ xét các node trong bán kính hợp lý (ví dụ 10-20m) để tối ưu vòng lặp
            // Nếu map nhỏ thì không cần filter này, duyệt allNodes cũng rất nhanh.
            .minByOrNull { node ->
                val distFromUserToNode = calculateDistance(currentUserPos, node)
                val distFromNodeToTarget = distancesToTarget[node.id] ?: Float.MAX_VALUE

                if (distFromNodeToTarget == Float.MAX_VALUE) Float.MAX_VALUE
                else distFromUserToNode + distFromNodeToTarget
            }

        if (bestStartNode == null) return

        // --- BƯỚC 3: Truy vết lại đường đi (Reconstruct Path) ---
        // Lưu ý: pathTree đang lưu dạng: Node hiện tại -> Node tiếp theo về phía đích
        val pathNodes = reconstructPath(bestStartNode.id, endGatewayNode.id, allNodes, pathTree)

        // --- BƯỚC 4: Tạo danh sách tọa độ để vẽ ---
        val pathOffsets = mutableListOf<Offset>()
        pathOffsets.add(currentUserPos) // Điểm 1: Vị trí người dùng
        pathOffsets.addAll(pathNodes.map { Offset(it.x, it.y) }) // Các điểm trên đồ thị

        // Thêm điểm đích cuối cùng
        if (endGatewayNode.id != targetNode.id || pathOffsets.size == 1) {
            pathOffsets.add(Offset(targetNode.x, targetNode.y))
        }

        _pathOffsetAndNode.value = Pair(pathOffsets, pathNodes)
    }

    // 2. Class chứa kết quả Dijkstra toàn cục
    data class DijkstraResult(
        val distances: Map<Long, Float>,
        val previous: Map<Long, Long>
    )

    // 3. Phiên bản Dijkstra chạy full map (không dừng khi gặp đích vì đích chính là điểm bắt đầu lan truyền)
    private fun runDijkstraFullMap(
        startId: Long, // Ở đây startId chính là Target Node
        nodes: List<Node>,
        edges: List<Edge>
    ): DijkstraResult {
        val adj = HashMap<Long, MutableList<Pair<Long, Float>>>()
        edges.forEach { edge ->
            val w = if (edge.weight > 0) edge.weight else 1f
            adj.computeIfAbsent(edge.fromNode) { ArrayList() }.add(edge.toNode to w)
            adj.computeIfAbsent(edge.toNode) { ArrayList() }.add(edge.fromNode to w)
        }

        val distances = HashMap<Long, Float>().withDefault { Float.MAX_VALUE }
        val previous = HashMap<Long, Long>()
        val priorityQueue = PriorityQueue<PathNode>()

        // Khởi tạo
        nodes.forEach { distances[it.id] = Float.MAX_VALUE }
        distances[startId] = 0f
        priorityQueue.add(PathNode(startId, 0f))

        while (priorityQueue.isNotEmpty()) {
            val (u, distU) = priorityQueue.poll() ?: break

            if (distU > (distances[u] ?: Float.MAX_VALUE)) continue

            // LƯU Ý QUAN TRỌNG: Không có dòng `if (u == endId) break` ở đây
            // Chúng ta muốn nó loang ra hết tất cả các node liên thông

            adj[u]?.forEach { (v, weight) ->
                val newDist = distU + weight
                if (newDist < (distances[v] ?: Float.MAX_VALUE)) {
                    distances[v] = newDist
                    previous[v] = u // Lưu lại: Để đến v nhanh nhất thì phải đi từ u
                    priorityQueue.add(PathNode(v, newDist))
                }
            }
        }
        return DijkstraResult(distances, previous)
    }

    // 4. Hàm truy vết đường đi từ Map kết quả
    private fun reconstructPath(
        startId: Long,
        endId: Long,
        allNodes: List<Node>,
        previousMap: Map<Long, Long>
    ): List<Node> {
        val path = mutableListOf<Node>()
        var curr: Long? = startId // Bắt đầu từ node gần user nhất

        // Truy ngược theo map `previous`: startId -> ... -> endId (Target)
        // Vì nãy ta chạy Dijkstra từ Target ra, nên `previous[startId]` chính là node tiếp theo hướng về Target.
        while (curr != null) {
            val node = allNodes.find { it.id == curr }
            if (node != null) path.add(node)

            if (curr == endId) break // Đã về đến đích
            curr = previousMap[curr]
        }

        // Không cần reverse vì ta đang đi xuôi từ Start -> End dựa trên cây đường đi ngắn nhất hội tụ về End
        return path
    }

//    fun findPathToNode(targetNode: Node) {
//        val currentUserPos = _userPosition.value ?: return
//        val allNodes = _nodes.value.filter { it.type != Node.HALLWAY }
//        val allEdges = _edges.value
//
//        val connectedNodeIds = allEdges.flatMap { listOf(it.fromNode, it.toNode) }.toSet()
//
//        val startNode = allNodes
//            .filter { it.id in connectedNodeIds }
//            .minByOrNull { calculateDistance(currentUserPos, it) }
//            ?: return
//
//        val endGatewayNode = if (targetNode.id in connectedNodeIds) {
//            targetNode
//        } else {
//            allNodes
//                .filter { it.id in connectedNodeIds && it.id != targetNode.id }
//                .minByOrNull {
//                    calculateDistance(
//                        Offset(
//                            targetNode.x,
//                            targetNode.y
//                        ), it
//                    )
//                }
//                ?: startNode
//        }
//
//        // Thay vì lấy 1 node, ta lấy Top 3 node gần người dùng nhất.
//        val candidateStartNodes = allNodes
//            .filter { it.id in connectedNodeIds }
//            .map { node ->
//                val dist = calculateDistance(currentUserPos, node)
//                node to dist
//            }
//            .sortedBy { it.second } // Sắp xếp theo khoảng cách tăng dần
//            .take(3) // Lấy 3 ứng viên tốt nhất
//
//        if (candidateStartNodes.isEmpty()) return
//
//        // 3. Chạy thuật toán tìm đường cho từng ứng viên và chọn đường tối ưu nhất
//        var bestPath: List<Node> = emptyList()
//        var minTotalCost = Float.MAX_VALUE
//
//        for ((startNode, distUserToStart) in candidateStartNodes) {
//            // Chạy Dijkstra từ ứng viên này đến đích
//            val pathNodes = runDijkstra(startNode.id, endGatewayNode.id, allNodes, allEdges)
//
//            if (pathNodes.isNotEmpty()) {
//                // Tính độ dài của đường đi trên đồ thị
//                val pathDistance = calculatePathDistance(pathNodes, allEdges)
//
//                // Tổng chi phí = Đi bộ đến node đầu + Đường đi trên đồ thị
//                val totalCost = distUserToStart + pathDistance
//
//                // Cập nhật nếu tìm được đường ngắn hơn
//                if (totalCost < minTotalCost) {
//                    minTotalCost = totalCost
//                    bestPath = pathNodes
//                }
//            }
//        }
//
//        if (bestPath.isEmpty()) {
//            // Xử lý trường hợp không tìm thấy đường
//            _pathOffsetAndNode.value = null
//            return
//        }
//
//        val pathOffsets = mutableListOf<Offset>()
//        pathOffsets.add(currentUserPos)
//        pathOffsets.addAll(bestPath.map { Offset(it.x, it.y) })
//
//        if (endGatewayNode.id != targetNode.id) {
//            pathOffsets.add(Offset(targetNode.x, targetNode.y))
//        } else if (pathOffsets.isEmpty()) {
//            pathOffsets.add(Offset(targetNode.x, targetNode.y))
//        }
//
//        _pathOffsetAndNode.value = Pair(pathOffsets, bestPath)
//    }

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

        val adj = HashMap<Long, MutableList<Pair<Long, Float>>>()
        edges.forEach { edge ->
            val w = if (edge.weight > 0) edge.weight else 1f
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

        val path = mutableListOf<Node>()
        var curr: Long? = endId

        if (previous[endId] == null && startId != endId) return emptyList()

        while (curr != null) {
            val node = nodes.find { it.id == curr }
            if (node != null) path.add(node)
            curr = previous[curr]
        }

        return path.reversed()
    }

    // Hàm phụ trợ để tính tổng độ dài của một path (List<Node>)
    private fun calculatePathDistance(nodes: List<Node>, edges: List<Edge>): Float {
        if (nodes.size < 2) return 0f
        var distance = 0f
        for (i in 0 until nodes.size - 1) {
            val u = nodes[i]
            val v = nodes[i + 1]
            // Tìm cạnh nối giữa u và v
            val edge = edges.find {
                (it.fromNode == u.id && it.toNode == v.id) ||
                        (it.fromNode == v.id && it.toNode == u.id)
            }
            // Nếu có weight thì dùng weight, không thì tính khoảng cách Euclid
            distance += edge?.weight ?: calculateDistance(Offset(u.x, u.y), v)
        }
        return distance
    }

    private fun calculateDistance(pos: Offset, node: Node): Float {
        return hypot(pos.x - node.x, pos.y - node.y)
    }
}

data class PathNode(val nodeId: Long, val distance: Float) : Comparable<PathNode> {
    override fun compareTo(other: PathNode): Int = this.distance.compareTo(other.distance)
}
