package ie.app.minimap.ui.ar

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.*
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isLocalized: Boolean = false
)

@HiltViewModel
class ArViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository,
    private val sessionManager: ArSessionManager
) : ViewModel() {

    private val TAG = "ArViewModelLog"

    private val _uiState = MutableStateFlow(ArUiState(loading = true))
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    private var referenceNodeId: Long? = null
    private val nodesAndAnchor: MutableMap<Long, Anchor> = mutableMapOf()
    private val resolvedNodeIds = mutableSetOf<Long>()
    private val resolvingNodeIds = mutableSetOf<Long>()

    private var allNodes: List<Node> = emptyList()
    private val pathLines = mutableListOf<com.google.ar.sceneform.Node>()
    private var currentPathNodes: List<Node> = emptyList()

    private var cachedDynamicRotation: Float? = null

    private var lastBatchTime = 0L
    private var lastLogicUpdateTime = 0L
    private var lastLocationUpdateTime = 0L

    private val LOGIC_UPDATE_INTERVAL = 1500L
    private val LOCATION_UPDATE_INTERVAL = 250L
    private val BATCH_SIZE = 6 // Giảm xuống để CPU thở phào
    private val BATCH_DURATION = 12000L

    private var logicJob: Job? = null
    private var lastPathIds: List<Long> = emptyList()

    // 1. INPUT FLOW: Nhận vị trí Camera từ UI (Tốc độ cao 60fps)
    // replay=0, extra=1, DROP_OLDEST: Chỉ giữ lại vị trí mới nhất, vứt bỏ quá khứ
    private val cameraPoseFlow = MutableSharedFlow<Pose>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 2. OUTPUT CHANNEL: Gửi lệnh "Cần tải Node này" ngược lại cho UI (Tốc độ thấp)
    private val _resolveRequestChannel = Channel<Node>(Channel.BUFFERED)
    val resolveRequestFlow = _resolveRequestChannel.receiveAsFlow()

    init {
        loadResources()
        startProcessingPipeline()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    private fun loadResources() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(loading = true) }
                sessionManager.loadModels()
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tải tài nguyên: ${e.message}")
                _uiState.update { it.copy(error = "Lỗi 3D: ${e.message}") }
            }
        }
    }

    fun updateCloudAnchors(newNodes: List<Node>) {
        if (allNodes == newNodes) return
        allNodes = newNodes
        val validIds = newNodes.map { it.id }.toSet()
        val deletedIds = nodesAndAnchor.keys.filter { it !in validIds }

        if (deletedIds.isNotEmpty()) {
            deletedIds.forEach { id ->
                nodesAndAnchor[id]?.detach()
                nodesAndAnchor.remove(id)
                resolvedNodeIds.remove(id)
                resolvingNodeIds.remove(id)
            }
        }

        if (referenceNodeId != null && referenceNodeId !in validIds) {
            referenceNodeId = null
            updateLocalizationState()
        }
    }

    fun onResume(arSceneView: ArSceneView) {
        try {
            val session = sessionManager.getOrCreateSession()
            if (arSceneView.session == null) arSceneView.setupSession(session)
            arSceneView.resume()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi OnResume: ${e.message}")
            _uiState.update { it.copy(error = e.message ?: "Lỗi AR") }
        }
    }

    private fun updateLocalizationState() {
        _uiState.update { it.copy(isLocalized = referenceNodeId != null) }
    }

    // --- PIPELINE XỬ LÝ (TRÁI TIM CỦA LOGIC) ---
    @OptIn(FlowPreview::class)
    private fun startProcessingPipeline() {
        cameraPoseFlow
            .filter { !uiState.value.loading && allNodes.isNotEmpty() } // Chỉ chạy khi sẵn sàng
            .sample(500L) // [QUAN TRỌNG] Chỉ lấy mẫu 500ms/lần (2Hz). Giảm tải CPU cực lớn.
            .map { cameraPose ->
                // --- CHẠY TRÊN THREAD TÍNH TOÁN (Default) ---
                // Tính toán xem cần tải node nào dựa trên vị trí camera lấy được
                findNodesToResolve(cameraPose)
            }
            .flowOn(Dispatchers.Default) // Đẩy toàn bộ logic map ở trên sang luồng Default
            .onEach { nodesToLoad ->
                // --- KẾT QUẢ TRẢ VỀ ---
                // Đẩy danh sách node cần tải vào Channel để UI tiêu thụ
                nodesToLoad.forEach { node ->
                    _resolveRequestChannel.trySend(node)
                }
            }
            .launchIn(viewModelScope) // Gắn vòng đời vào ViewModel
    }

    // Hàm thuần túy tính toán (Pure Function): Input Pose -> Output List<Node>
    private fun findNodesToResolve(cameraPose: Pose): List<Node> {
        // 1. Chưa có Reference -> Không làm gì (hoặc logic scanning riêng)
        if (referenceNodeId == null) return emptyList()

        // 2. Tạo danh sách ưu tiên (Đường đi trước, còn lại sau)
        val processingList = if (currentPathNodes.isNotEmpty()) {
            val pathIds = currentPathNodes.map { it.id }.toSet()
            currentPathNodes + allNodes.filter { it.id !in pathIds }
        } else {
            allNodes
        }

        val nodesToLoad = mutableListOf<Node>()
        var count = 0

        for (node in processingList) {
            // Giới hạn chỉ trả về tối đa 2 node mỗi lần quét để tránh nghẽn
            if (count >= 2) break

            // Bỏ qua nếu đang xử lý
            if (node.id in resolvedNodeIds || node.id in resolvingNodeIds) continue

            val isPathNode = currentPathNodes.any { it.id == node.id }

            // Logic tính toán vị trí dự đoán
            val predictedPos = calculatePredictedWorldPosition(node)

            var shouldLoad = false
            if (predictedPos != null) {
                val distSq = distanceSquared(cameraPose.tx(), 0f, cameraPose.tz(), predictedPos.x, 0f, predictedPos.z)
                // Khoảng cách: Đường đi (20m/400f) hoặc Node thường (6m/36f)
                val threshold = if (isPathNode) 400.0f else 36.0f
                if (distSq < threshold) shouldLoad = true
            } else if (isPathNode && cachedDynamicRotation == null) {
                // Mù hướng nhưng là đường đi -> Tải đại
                shouldLoad = true
            }

            if (shouldLoad) {
                nodesToLoad.add(node)
                count++
            }
        }
        return nodesToLoad
    }

    fun onUpdate(arSceneView: ArSceneView) {
        val frame = arSceneView.arFrame ?: return
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return

        // 1. Chỉ cập nhật vị trí mới nhất vào Flow (Non-blocking)
        cameraPoseFlow.tryEmit(camera.pose)

        // 2. Logic cập nhật Reference Anchor (Giữ nguyên ở Main thread vì cần truy cập ARCore ngay)
        if (referenceNodeId == null) {
            // Tìm anchor gần nhất đang track để làm mốc
            val closest = nodesAndAnchor
                .filter { it.value.trackingState == TrackingState.TRACKING }
                .minByOrNull { (_, anchor) ->
                    val pose = camera.pose
                    distanceSquared(pose.tx(), 0f, pose.tz(), anchor.pose.tx(), 0f, anchor.pose.tz())
                }
            if (closest != null) updateReferenceAnchor(closest.key)
        }
    }

    // --- HÀM THỰC THI RESOLVE (Được gọi từ UI khi nhận tín hiệu từ Channel) ---
    fun executeResolveNode(arSceneView: ArSceneView, node: Node) {
        // Kiểm tra lại lần cuối (Double check)
        if (node.id in resolvedNodeIds || node.id in resolvingNodeIds) return

        resolvingNodeIds.add(node.id)
        val session = arSceneView.session ?: return

        // Gọi ARCore API (Bất đồng bộ)
        try {
            session.resolveCloudAnchorAsync(node.cloudAnchorId) { anchor, state ->
                if (state == Anchor.CloudAnchorState.SUCCESS) {
                    resolvingNodeIds.remove(node.id)
                    resolvedNodeIds.add(node.id)
                    nodesAndAnchor[node.id] = anchor

                    val model = sessionManager.modelRenderable
                    if (model != null && node.type != Node.HALLWAY) {
                        placeObject(arSceneView, anchor, model)
                    }

                    if (referenceNodeId == null) updateReferenceAnchor(node.id)

                    // Vẽ lại đường nếu cần
                    if (currentPathNodes.any { it.id == node.id }) {
                        // drawPath(arSceneView, currentPathNodes, force = true) // Uncomment nếu cần vẽ lại
                    }
                } else if (state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
                    resolvingNodeIds.remove(node.id)
                }
            }
        } catch (_: Exception) {
            resolvingNodeIds.remove(node.id)
        }
    }

//    fun onUpdate(arSceneView: ArSceneView) {
//        if (_uiState.value.loading || allNodes.isEmpty()) return
//
//        val arFrame = arSceneView.arFrame ?: return
//        val camera = arFrame.camera
//        if (camera.trackingState != TrackingState.TRACKING) return
//
//        val currentTime = System.currentTimeMillis()
//
//        if (currentTime - lastLogicUpdateTime < LOGIC_UPDATE_INTERVAL) return
//        if (logicJob?.isActive == true) return
//
//        lastLogicUpdateTime = currentTime
//        val cameraPose = camera.pose
//
//        if (referenceNodeId == null) {
//            for ((nodeId, anchor) in nodesAndAnchor) {
//                if (anchor.trackingState == TrackingState.TRACKING) {
//                    updateReferenceAnchor(nodeId)
//                    break
//                }
//            }
//        }
//
//        logicJob = viewModelScope.launch(Dispatchers.Default) {
//            if (referenceNodeId == null) {
//                if (currentTime - lastBatchTime > BATCH_DURATION || lastBatchTime == 0L) {
//                    withContext(Dispatchers.Main) {
//                        rotateToNextBatch(arSceneView)
//                    }
//                    lastBatchTime = currentTime
//                }
//            } else {
//                val processingList = if (currentPathNodes.isNotEmpty()) {
//                    val pathIds = currentPathNodes.map { it.id }.toSet()
//                    currentPathNodes + allNodes.filter { it.id !in pathIds }
//                } else {
//                    allNodes
//                }
//
//                processingList.forEach { node ->
//                    if (node.id !in resolvedNodeIds && node.id !in resolvingNodeIds) {
//                        val isPathNode = currentPathNodes.any { it.id == node.id }
//                        val predictedPos = calculatePredictedWorldPosition(node)
//
//                        if (predictedPos != null) {
//                            val distSq = distanceSquared(cameraPose.tx(), 0f, cameraPose.tz(), predictedPos.x, 0f, predictedPos.z)
//                            val threshold = if (isPathNode) 144.0f else 36.0f
//
//                            if (distSq < threshold) {
//                                withContext(Dispatchers.Main) {
//                                    resolveNodeIfNotBusy(arSceneView, node)
//                                }
//                            }
//                        } else if (isPathNode && cachedDynamicRotation == null) {
//                            withContext(Dispatchers.Main) {
//                                resolveNodeIfNotBusy(arSceneView, node)
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun distanceSquared(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
    }

//    private fun rotateToNextBatch(arSceneView: ArSceneView) {
//        val scanList = if (currentPathNodes.isNotEmpty()) {
//            val pathIds = currentPathNodes.map { it.id }.toSet()
//            currentPathNodes + allNodes.filter { it.id !in pathIds }
//        } else allNodes
//
//        scanList.take(BATCH_SIZE).forEach { node ->
//            resolveNodeIfNotBusy(arSceneView, node)
//        }
//    }

//    private fun resolveNodeIfNotBusy(arSceneView: ArSceneView, node: Node) {
//        if (node.id in resolvedNodeIds || node.id in resolvingNodeIds || node.cloudAnchorId.isBlank()) return
//        resolvingNodeIds.add(node.id)
//        val session = arSceneView.session ?: return
//        try {
//            session.resolveCloudAnchorAsync(node.cloudAnchorId) { anchor, state ->
//                if (state == Anchor.CloudAnchorState.SUCCESS) {
//                    resolvingNodeIds.remove(node.id)
//                    resolvedNodeIds.add(node.id)
//                    nodesAndAnchor[node.id] = anchor
//                    val model = sessionManager.modelRenderable
//                    if (model != null && node.type != Node.HALLWAY) {
//                        placeObject(arSceneView, anchor, model)
//                    }
//                    updateReferenceAnchor(node.id)
//                    if (currentPathNodes.any { it.id == node.id }) {
//                        drawPath(arSceneView, currentPathNodes, force = true)
//                    }
//                } else if (state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
//                    resolvingNodeIds.remove(node.id)
//                }
//            }
//        } catch (_: Exception) {
//            resolvingNodeIds.remove(node.id)
//        }
//    }

    private fun updateReferenceAnchor(nodeId: Long) {
        if (referenceNodeId != nodeId) {
            referenceNodeId = nodeId
            updateLocalizationState()
        }
    }

    private fun calculatePredictedWorldPosition(targetNode: Node): Vector3? {
        val refId = referenceNodeId ?: return null
        val refAnchor = nodesAndAnchor[refId] ?: return null
        val refNode = allNodes.find { it.id == refId } ?: return null

        val scaleFactor = 150f
        val dxMap = targetNode.x - refNode.x
        val dyMap = targetNode.y - refNode.y

        val rot = -(cachedDynamicRotation ?: 0f)
        val rad = Math.toRadians(rot.toDouble())
        val sinT = kotlin.math.sin(rad)
        val cosT = kotlin.math.cos(rad)

        val arDx = dxMap * cosT - dyMap * sinT
        val arDz = dxMap * sinT + dyMap * cosT

        return Vector3(
            refAnchor.pose.tx() + (arDx / scaleFactor).toFloat(),
            refAnchor.pose.ty(),
            refAnchor.pose.tz() + (arDz / scaleFactor).toFloat()
        )
    }

    fun onPause(arSceneView: ArSceneView) {
        arSceneView.pause()
        logicJob?.cancel()
    }

    fun onDestroyView(arSceneView: ArSceneView) {
        arSceneView.pause()
        arSceneView.destroy()

        sessionManager.clearRenderables()
        logicJob?.cancel()
        sessionManager.destroySession()
    }

    fun updateUserLocationFromWorld(cameraPose: Pose): Offset? {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLocationUpdateTime < LOCATION_UPDATE_INTERVAL) return null

        val visibleAnchors = nodesAndAnchor.filter { it.value.trackingState == TrackingState.TRACKING }.entries.toList()
        if (visibleAnchors.isEmpty()) return null

        lastLocationUpdateTime = currentTime

        val closestAnchorEntry = visibleAnchors.minByOrNull { (_, anchor) ->
            val dx = cameraPose.tx() - anchor.pose.tx()
            val dz = cameraPose.tz() - anchor.pose.tz()
            dx * dx + dz * dz
        } ?: return null

        updateReferenceAnchor(closestAnchorEntry.key)

        if (visibleAnchors.size >= 2) {
            val (idA, anchorA) = visibleAnchors[0]
            val (idB, anchorB) = visibleAnchors[1]
            val nodeA = allNodes.find { it.id == idA }
            val nodeB = allNodes.find { it.id == idB }

            if (nodeA != null && nodeB != null) {
                val arDx = anchorB.pose.tx() - anchorA.pose.tx()
                val arDz = anchorB.pose.tz() - anchorA.pose.tz()
                val angleAR = kotlin.math.atan2(arDz, arDx)
                val mapDx = nodeB.x - nodeA.x
                val mapDy = nodeB.y - nodeA.y
                val angleMap = kotlin.math.atan2(mapDy, mapDx)

                var diff = angleMap - angleAR
                while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
                while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
                cachedDynamicRotation = Math.toDegrees(diff.toDouble()).toFloat()
            }
        }

        return calculateMapCoordinateFromPose(cameraPose)
    }

    private fun calculateMapCoordinateFromPose(targetPose: Pose): Offset? {
        val refId = referenceNodeId ?: return null
        val refAnchor = nodesAndAnchor[refId] ?: return null
        val refNode = allNodes.find { it.id == refId } ?: return null

        val dx = targetPose.tx() - refAnchor.pose.tx()
        val dz = targetPose.tz() - refAnchor.pose.tz()

        val rad = Math.toRadians((cachedDynamicRotation ?: 0f).toDouble())
        val sinT = kotlin.math.sin(rad)
        val cosT = kotlin.math.cos(rad)

        val rotatedDx = dx * cosT - dz * sinT
        val rotatedDz = dx * sinT + dz * cosT

        val scale = 150f
        return Offset(refNode.x + (rotatedDx * scale).toFloat(), refNode.y + (rotatedDz * scale).toFloat())
    }

    fun onSceneTouched(arSceneView: ArSceneView, pose: Pose, type: String, name: String?, description: String?, vendorName: String?, vendorDescription: String?, floor: Floor, building: Building, venueId: Long) {
        val model = sessionManager.modelRenderable ?: return
        val hallway = sessionManager.hallwayRenderable ?: return
        val session = arSceneView.session ?: return
        val localAnchor = session.createAnchor(pose)
        placeObject(arSceneView, localAnchor, if (type == Node.HALLWAY) hallway else model)

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = "Đang đồng bộ Cloud...") }
            try {
                val cloudId = hostCloudAnchor(localAnchor)
                if (cloudId != null) {
                    var pos = calculateMapCoordinateFromPose(pose) ?: Offset(0f, 0f)
                    val newNode = Node(venueId = venueId, floorId = floor.id, x = pos.x, y = pos.y, type = type, cloudAnchorId = cloudId)
                    val nodeId = mapRepository.upsertNode(newNode)

                    if (type == Node.BOOTH) {
                        val vendorId = if (vendorName != null && vendorDescription != null) infoRepository.upsertVendor(Vendor(venueId = venueId, name = vendorName, description = vendorDescription)) else 0
                        if (name != null && description != null) {
                            val shapeId = mapRepository.upsertShape(Shape(venueId = venueId, nodeId = nodeId, centerX = pos.x, centerY = pos.y, width = 120f, height = 80f, label = name, shape = Shape.Companion.ShapeType.RECTANGLE, color = 0xFF3B82F6))
                            infoRepository.upsertBooth(Booth(nodeId = nodeId, shapeId = shapeId, vendorId = vendorId, floorId = floor.id, buildingId = building.id, venueId = venueId, name = name, description = description))
                        }
                    } else if (type == Node.ROOM && name != null) {
                        mapRepository.upsertShape(Shape(venueId = venueId, nodeId = nodeId, centerX = pos.x, centerY = pos.y, width = 120f, height = 80f, label = name, shape = Shape.Companion.ShapeType.RECTANGLE, color = 0xFF3B82F6))
                    }

                    withContext(Dispatchers.Main) {
                        resolvedNodeIds.add(nodeId)
                        nodesAndAnchor[nodeId] = localAnchor
                        updateReferenceAnchor(nodeId)
                        _uiState.update { it.copy(loading = false, message = "Lưu thành công!") }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        localAnchor.detach()
                        _uiState.update { it.copy(loading = false, message = "Lỗi lưu Cloud Anchor") }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    localAnchor.detach()
                    _uiState.update { it.copy(loading = false, message = "Lỗi: ${e.message}") }
                }
            }
        }
    }

    private suspend fun hostCloudAnchor(localAnchor: Anchor): String? {
        return suspendCancellableCoroutine { cont ->
            val session = sessionManager.arSession ?: return@suspendCancellableCoroutine
            try {
                session.hostCloudAnchorAsync(localAnchor, 1) { cloudId, state ->
                    if (state == Anchor.CloudAnchorState.SUCCESS) cont.resume(cloudId, null)
                    else if (state != Anchor.CloudAnchorState.TASK_IN_PROGRESS) cont.resume(null, null)
                }
            } catch (e: Exception) { cont.resume(null, null) }
        }
    }

    // TỐI ƯU: Sử dụng Node thay vì TransformableNode để giảm CPU
    private fun placeObject(arSceneView: ArSceneView, anchor: Anchor, model: ModelRenderable) {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arSceneView.scene)
        val modelNode = com.google.ar.sceneform.Node()
        modelNode.setParent(anchorNode)
        modelNode.renderable = model
    }

    fun drawPath(arSceneView: ArSceneView, pathNode: List<Node>, force: Boolean = false) {
        val newNodeIds = pathNode.map { it.id }
        if (!force && newNodeIds == lastPathIds) return
        lastPathIds = newNodeIds
        currentPathNodes = pathNode

        viewModelScope.launch(Dispatchers.Main) {
            clearPathLines()
            val pathRenderable = sessionManager.pathRenderable ?: return@launch
            for (i in 0 until pathNode.size - 1) {
                val startAnchor = nodesAndAnchor[pathNode[i].id]
                val endAnchor = nodesAndAnchor[pathNode[i+1].id]
                if (startAnchor != null && endAnchor != null) {
                    val p1 = Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
                    val p2 = Vector3(endAnchor.pose.tx(), endAnchor.pose.ty(), endAnchor.pose.tz())
                    drawLine(arSceneView, p1, p2, pathRenderable)
                }
            }
        }
    }

    private fun drawLine(
        arSceneView: ArSceneView,
        point1: Vector3,
        point2: Vector3,
        renderable: ModelRenderable
    ) {
        val scene = arSceneView.scene ?: return
        val difference = Vector3.subtract(point2, point1)
        val stepSize = 0.15f
        val steps = (difference.length() / stepSize).toInt()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val position = Vector3.add(point1, difference.scaled(t))
            val node = com.google.ar.sceneform.Node().apply {
                setParent(scene)
                this.renderable = renderable
                worldPosition = position
            }
            pathLines.add(node)
        }
    }

    private fun clearPathLines() {
        pathLines.forEach { it.setParent(null) }
        pathLines.clear()
    }
}
