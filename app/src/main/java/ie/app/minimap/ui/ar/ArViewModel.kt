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
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.*
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val sessionManager: ArSessionManager // Inject Singleton Manager
) : ViewModel() {

    private val TAG = "ArViewModelLog"

    private val _uiState = MutableStateFlow(ArUiState(loading = true))
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    //    private var referenceAnchorPose: Pair<String, Pose>? = null
    private var referenceNodeId: Long? = null

    private val nodesAndAnchor: MutableMap<Long, Anchor> = mutableMapOf()
    private val resolvedNodeIds = mutableSetOf<Long>()
    private val resolvingNodeIds = mutableSetOf<Long>()

    private var allNodes: List<Node> = emptyList()
    private val pathLines = mutableListOf<com.google.ar.sceneform.Node>()
    private var currentPathNodes: List<Node> = emptyList()

    private var mapCalibrationRotation: Float = 0f
    private var cachedDynamicRotation: Float? = null

    private var currentBatchIndex = 0
    private var lastBatchTime = 0L
    private var lastLogicUpdateTime = 0L
    private val LOGIC_UPDATE_INTERVAL = 500L
    private val BATCH_SIZE = 20
    private val BATCH_DURATION = 8000L

    init {
        Log.d(TAG, "Init ViewModel")
        loadResources()
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
                _uiState.update {
                    it.copy(
                        error = "Không thể tải mô hình 3D: ${e.message}",
                        message = "Lỗi tải tài nguyên 3D"
                    )
                }
            }
        }
    }

    fun updateCloudAnchors(newNodes: List<Node>) {
        allNodes = newNodes
        val validIds = newNodes.map { it.id }.toSet()
        val currentCachedIds = nodesAndAnchor.keys.toSet()
        val deletedIds = currentCachedIds.filter { it !in validIds }

        if (deletedIds.isNotEmpty()) {
            deletedIds.forEach { id ->
                nodesAndAnchor[id]?.detach()
                nodesAndAnchor.remove(id)
                resolvedNodeIds.remove(id)
                resolvingNodeIds.remove(id)
            }
        }

//        if (referenceAnchorPose != null) {
//            val (refCloudId, _) = referenceAnchorPose!!
//            val isRefStillValid = newNodes.any { it.cloudAnchorId == refCloudId }
//            if (!isRefStillValid) {
//                referenceAnchorPose = null
//                updateLocalizationState()
//                currentBatchIndex = 0
//                lastBatchTime = 0L
//            }
//        }
        if (referenceNodeId != null && referenceNodeId !in validIds) {
            referenceNodeId = null
            updateLocalizationState()
        }

        if (newNodes.isEmpty()) {
            currentBatchIndex = 0
            lastBatchTime = 0L
            showMessage("Không có dữ liệu điểm mốc.")
        }
    }

    fun onResume(arSceneView: ArSceneView) {
        try {
            val session = sessionManager.getOrCreateSession()
            if (arSceneView.session == null) arSceneView.setupSession(session)
            arSceneView.resume()
            Log.d(TAG, "AR Session Resumed via Manager")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi OnResume: ${e.message}")
            _uiState.update {
                it.copy(
                    error = e.message ?: "Lỗi AR",
                    message = "Không thể khởi động Camera AR."
                )
            }
        }
    }

    private fun updateLocalizationState() {
        _uiState.update {
            it.copy(isLocalized = referenceNodeId != null)
        }
    }

//    fun onUpdate(arSceneView: ArSceneView, transformationSystem: TransformationSystem) {
//        val currentState = _uiState.value
//        if (currentState.loading) return
//        if (allNodes.isEmpty()) return
//
//        val currentTime = System.currentTimeMillis()
//        if (currentTime - lastLogicUpdateTime < LOGIC_UPDATE_INTERVAL) {
//            return
//        }
//        lastLogicUpdateTime = currentTime
//
//        val arFrame = arSceneView.arFrame ?: return
//        val camera = arFrame.camera
//        if (camera.trackingState != TrackingState.TRACKING) return
//
//        val cameraPose = camera.pose
//
////        if (referenceAnchorPose == null) {
////            for ((nodeId, anchor) in nodesAndAnchor) {
////                if (anchor.trackingState == TrackingState.TRACKING) {
////                    val node = allNodes.find { it.id == nodeId }
////                    if (node != null) {
////                        referenceAnchorPose = node.cloudAnchorId to anchor.pose
////                        updateLocalizationState()
////                        break
////                    }
////                }
////            }
////        }
//        if (referenceNodeId == null) {
//            for ((nodeId, anchor) in nodesAndAnchor) {
//                if (anchor.trackingState == TrackingState.TRACKING) {
//                    // Tìm thấy anchor đầu tiên đang track -> Chọn làm gốc
//                    updateReferenceAnchor(nodeId)
//                    break
//                }
//            }
//        }
//
//        if (referenceNodeId == null) {
//            val currentTime = System.currentTimeMillis()
//            if (allNodes.size > BATCH_SIZE) {
//                if (currentTime - lastBatchTime > BATCH_DURATION) {
//                    rotateToNextBatch(arSceneView, transformationSystem)
//                    lastBatchTime = currentTime
//                }
//            } else {
//                if (resolvingNodeIds.isEmpty() && resolvedNodeIds.isEmpty()) {
//                    rotateToNextBatch(arSceneView, transformationSystem)
//                }
//            }
//        } else {
//            viewModelScope.launch(Dispatchers.Default) {
//                val priorityNodes = currentPathNodes.ifEmpty { allNodes }
//                priorityNodes.forEach { node ->
//                    if (node.id !in resolvedNodeIds && node.id !in resolvingNodeIds) {
//                        val predictedWorldPos = calculatePredictedWorldPosition(node)
//                        if (predictedWorldPos != null) {
//                            val dist = distanceSquared(
//                                cameraPose.tx(), 0f, cameraPose.tz(),
//                                predictedWorldPos.x, 0f, predictedWorldPos.z
//                            )
//                            if (dist < 64.0f) {
//                                resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
//                            }
//                        }
//                    }
//                }
//                if (currentPathNodes.isNotEmpty()) {
//                    allNodes.forEach { node ->
//                        if (node.id !in resolvedNodeIds && node.id !in resolvingNodeIds) {
//                            val predictedWorldPos = calculatePredictedWorldPosition(node)
//                            if (predictedWorldPos != null) {
//                                val dist = distanceSquared(
//                                    cameraPose.tx(), 0f, cameraPose.tz(),
//                                    predictedWorldPos.x, 0f, predictedWorldPos.z
//                                )
//                                if (dist < 64.0f) {
//                                    resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
fun onUpdate(arSceneView: ArSceneView, transformationSystem: TransformationSystem) {
    val currentState = _uiState.value
    if (currentState.loading) return
    if (allNodes.isEmpty()) return

    val currentTime = System.currentTimeMillis()
    if (currentTime - lastLogicUpdateTime < LOGIC_UPDATE_INTERVAL) return
    lastLogicUpdateTime = currentTime

    val arFrame = arSceneView.arFrame ?: return
    val camera = arFrame.camera
    if (camera.trackingState != TrackingState.TRACKING) return

    val cameraPose = camera.pose

    // 1. Tự động chọn Reference Anchor nếu chưa có
    if (referenceNodeId == null) {
        for ((nodeId, anchor) in nodesAndAnchor) {
            if (anchor.trackingState == TrackingState.TRACKING) {
                // Tìm thấy anchor đầu tiên đang track -> Chọn làm gốc
                updateReferenceAnchor(nodeId)
                break
            }
        }
    }

    // 2. Logic xoay vòng resolve (Scanning)
    if (referenceNodeId == null) {
        if (allNodes.size > BATCH_SIZE) {
            if (currentTime - lastBatchTime > BATCH_DURATION) {
                rotateToNextBatch(arSceneView, transformationSystem)
                lastBatchTime = currentTime
            }
        } else {
            if (resolvingNodeIds.isEmpty() && resolvedNodeIds.isEmpty()) {
                rotateToNextBatch(arSceneView, transformationSystem)
            }
        }
    } else {
        // 3. Logic Resolve thông minh (Proximity)
        viewModelScope.launch(Dispatchers.Default) {

            // Danh sách ưu tiên: Đường đi -> Còn lại
            val processingList = if (currentPathNodes.isNotEmpty()) {
                val pathIds = currentPathNodes.map { it.id }.toSet()
                currentPathNodes + allNodes.filter { it.id !in pathIds }
            } else {
                allNodes
            }

            processingList.forEach { node ->
                if (node.id !in resolvedNodeIds && node.id !in resolvingNodeIds) {

                    // [FIX LOGIC DEADLOCK TẠI ĐÂY]
                    // Nếu chưa biết góc xoay (cachedDynamicRotation == null)
                    // VÀ node này thuộc đường đi (isPathNode)
                    // -> Ta CƯỠNG ÉP resolve nó luôn mà không cần check khoảng cách kỹ càng
                    // (Hoặc check lỏng lẻo hơn)

                    val isPathNode = currentPathNodes.any { it.id == node.id }
                    val isRotationUnknown = cachedDynamicRotation == null

                    val predictedWorldPos = calculatePredictedWorldPosition(node)

                    if (predictedWorldPos != null) {
                        val distSq = distanceSquared(
                            cameraPose.tx(), 0f, cameraPose.tz(),
                            predictedWorldPos.x, 0f, predictedWorldPos.z
                        )

                        // Ngưỡng khoảng cách:
                        // - Nếu chưa biết góc xoay + là Path Node: Cho phép sai số cực lớn (20m -> 400f)
                        //   để cố gắng bắt được điểm thứ 2.
                        // - Nếu đã biết góc xoay: Dùng ngưỡng chuẩn (10m -> 100f).

                        val threshold = if (isPathNode) {
                            if (isRotationUnknown) 400.0f else 100.0f
                        } else {
                            36.0f // Node thường (6m)
                        }

                        if (distSq < threshold) {
                            withContext(Dispatchers.Main) {
                                resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
                            }
                        }
                    } else if (isPathNode && isRotationUnknown) {
                        // Nếu thậm chí không tính được vị trí dự đoán (do lỗi gì đó),
                        // nhưng đây là node đường đi và ta đang mù hướng -> Cứ thử resolve đại đi!
                        withContext(Dispatchers.Main) {
                            resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
                        }
                    }
                }
            }
        }
    }
}

    private fun distanceSquared(
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float
    ): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        val dz = z1 - z2
        return dx * dx + dy * dy + dz * dz
    }

    private fun rotateToNextBatch(
        arSceneView: ArSceneView,
        transformationSystem: TransformationSystem,
    ) {
        val totalNodes = allNodes.size
        if (totalNodes == 0) return

        resolvingNodeIds.clear()

        // [TỐI ƯU] Tạo danh sách quét: Ưu tiên đường đi -> Landmark -> Còn lại
        val scanList = if (currentPathNodes.isNotEmpty()) {
            val pathIds = currentPathNodes.map { it.id }.toSet()
            // Đưa path lên đầu
            currentPathNodes + allNodes.filter { it.id !in pathIds }
        } else {
            allNodes
        }

        // Logic batching cũ (nhưng áp dụng trên scanList đã sắp xếp)
        // Lưu ý: Để đơn giản, ta có thể bỏ qua logic batch index phức tạp nếu số lượng node < 50
        // và chỉ quét 20 node đầu tiên trong priority list.

        val batchNodes = scanList.take(BATCH_SIZE) // Lấy 20 node quan trọng nhất quét trước

        batchNodes.forEach { node ->
            resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
        }
    }

//    private fun rotateToNextBatch(
//        arSceneView: ArSceneView,
//        transformationSystem: TransformationSystem,
//    ) {
//        val totalNodes = allNodes.size
//        if (totalNodes == 0) return
//
//        resolvingNodeIds.clear()
//
//        val startIndex = currentBatchIndex
//        var endIndex = startIndex + BATCH_SIZE
//        if (endIndex > totalNodes) {
//            endIndex = totalNodes
//            currentBatchIndex = 0
//        } else {
//            currentBatchIndex += BATCH_SIZE
//            if (currentBatchIndex >= totalNodes) currentBatchIndex = 0
//        }
//
//        val rotatingNodes = allNodes.subList(startIndex, endIndex)
//        val landmarkNodes = allNodes.filter {
//            it.type == Node.CONNECTOR || it.type == Node.INTERSECTION
//        }.take(5)
//
//        val nodesToScan = (rotatingNodes + landmarkNodes).distinctBy { it.id }
//
//        nodesToScan.forEach { node ->
//            resolveNodeIfNotBusy(arSceneView, transformationSystem, node)
//        }
//    }

    private fun resolveNodeIfNotBusy(
        arSceneView: ArSceneView,
        transformationSystem: TransformationSystem,
        node: Node
    ) {
        if (node.id in resolvedNodeIds || node.id in resolvingNodeIds) return
        if (node.cloudAnchorId.isBlank()) return

        resolvingNodeIds.add(node.id)

        val session = arSceneView.session ?: return
        try {
            session.resolveCloudAnchorAsync(node.cloudAnchorId) { anchor, state ->
                when (state) {
                    Anchor.CloudAnchorState.SUCCESS -> {
                        resolvingNodeIds.remove(node.id)
                        resolvedNodeIds.add(node.id)
                        nodesAndAnchor[node.id] = anchor

                        val model = sessionManager.modelRenderable
                        if (model != null && node.type != Node.HALLWAY) {
                            placeObject(arSceneView, anchor, model, transformationSystem)
                        }
                        updateReferenceAnchor(node.id)
                        if (currentPathNodes.any { it.id == node.id }) {
//                            refreshPathVisuals(arSceneView)
                            drawPath(arSceneView,currentPathNodes)
                        }
                    }

                    Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {}
                    else -> resolvingNodeIds.remove(node.id)
                }
            }
        } catch (_: Exception) {
            resolvingNodeIds.remove(node.id)
        }
    }

    private fun autoCalculateMapRotation(anchorA_Pose: Pose, anchorB_Pose: Pose) {
        val arDx = anchorB_Pose.tx() - anchorA_Pose.tx()
        val arDz = anchorB_Pose.tz() - anchorA_Pose.tz()
        val angleAR = kotlin.math.atan2(arDz, arDx)
        val diff = 0.0 - angleAR
        cachedDynamicRotation = Math.toDegrees(diff).toFloat()
    }

    private fun updateReferenceAnchor(nodeId: Long) {
        referenceNodeId = nodeId
        updateLocalizationState()
    }

//    fun setPath(arSceneView: ArSceneView, pathNode: List<Node>) {
//        if (pathNode.isEmpty()) {
//            currentPathNodes = emptyList()
//            clearPathLines()
//            return
//        }
//        currentPathNodes = pathNode
//
//        // Thử vẽ ngay lập tức (với những điểm đã có sẵn anchor)
//        refreshPathVisuals(arSceneView)
//    }

    // Hàm nội bộ: Vẽ lại các đoạn đường dựa trên Anchor hiện có
//    private fun refreshPathVisuals(view: ArSceneView) {
//        val res = sessionManager.pathRenderable ?: return
//        if (currentPathNodes.isEmpty()) {
//            clearPathLines(); return
//        }
//
//        viewModelScope.launch(Dispatchers.Main) {
//            clearPathLines()
//            for (i in 0 until currentPathNodes.size - 1) {
//                val start = currentPathNodes[i];
//                val end = currentPathNodes[i + 1]
//                val aStart = nodesAndAnchor[start.id];
//                val aEnd = nodesAndAnchor[end.id]
//                if (aStart != null && aEnd != null) {
//                    val p1 = Vector3(aStart.pose.tx(), aStart.pose.ty(), aStart.pose.tz())
//                    val p2 = Vector3(aEnd.pose.tx(), aEnd.pose.ty(), aEnd.pose.tz())
//                    drawLine(view, p1, p2, res)
//                }
//            }
//        }
//    }

    //    private fun calculatePredictedWorldPosition(targetNode: Node, allNodes: List<Node>): Vector3? {
//        if (referenceAnchorPose == null) return null
//        val (refCloudId, refPose) = referenceAnchorPose!!
//        val refNode = allNodes.find { it.cloudAnchorId == refCloudId } ?: return null
//        val scaleFactor = 150f
//        val dxMap = targetNode.x - refNode.x
//        val dyMap = targetNode.y - refNode.y
//        return Vector3(refPose.tx() + dxMap / scaleFactor, 0f, refPose.tz() + dyMap / scaleFactor)
    private fun calculatePredictedWorldPosition(targetNode: Node): Vector3? {
        val refId = referenceNodeId ?: return null
        val refAnchor = nodesAndAnchor[refId] ?: return null
        val refNode = allNodes.find { it.id == refId } ?: return null

        val scaleFactor = 150f
        val dxMap = targetNode.x - refNode.x
        val dyMap = targetNode.y - refNode.y

        // Ngược lại của logic xoay (Map -> World)
        val rot = -(cachedDynamicRotation ?: 0f)
        val rad = Math.toRadians(rot.toDouble())
        val sinT = kotlin.math.sin(rad)
        val cosT = kotlin.math.cos(rad)

        val arDx = dxMap * cosT - dyMap * sinT
        val arDz = dxMap * sinT + dyMap * cosT

        return Vector3(
            refAnchor.pose.tx() + (arDx / scaleFactor).toFloat(),
            0f,
            refAnchor.pose.tz() + (arDz / scaleFactor).toFloat()
        )
    }


    fun onPause(arSceneView: ArSceneView) {
        arSceneView.pause()
    }

    fun onDestroyView(arSceneView: ArSceneView) {
        arSceneView.pause()
        // We do NOT close the session here, as it's managed by the Singleton Manager
        arSceneView.destroy()
        sessionManager.clearRenderables()
    }

    fun updateUserLocationFromWorld(cameraPose: Pose): Offset? {
        // Lấy danh sách các anchor đang nhìn thấy (Tracking)
        val visibleAnchors = nodesAndAnchor.filter {
            it.value.trackingState == TrackingState.TRACKING
        }.entries.toList()

        if (visibleAnchors.isEmpty()) return null

        // TÌM ANCHOR GẦN CAMERA NHẤT ĐỂ LÀM GỐC (GIẢM SAI SỐ)
        val closestAnchorEntry = visibleAnchors.minByOrNull { (_, anchor) ->
            val dx = cameraPose.tx() - anchor.pose.tx()
            val dz = cameraPose.tz() - anchor.pose.tz()
            dx * dx + dz * dz
        } ?: return null

        val (closestId, _) = closestAnchorEntry

        // Cập nhật Reference ID sang điểm gần nhất này (để các tính toán sau dùng nó)
        if (referenceNodeId != closestId) {
            updateReferenceAnchor(closestId)
        }

        // --- LOGIC AUTO ALIGN (TÍNH GÓC) ---
        if (visibleAnchors.size >= 2) {
            val (idA, anchorA) = visibleAnchors[0]
            val (idB, anchorB) = visibleAnchors[1]
            val nodeA = allNodes.find { it.id == idA }
            val nodeB = allNodes.find { it.id == idB }

            if (nodeA != null && nodeB != null) {
                // Vector AR (Thực tế)
                val arDx = anchorB.pose.tx() - anchorA.pose.tx()
                val arDz = anchorB.pose.tz() - anchorA.pose.tz()
                val angleAR = kotlin.math.atan2(arDz, arDx)

                // Vector Map (Bản vẽ)
                val mapDx = nodeB.x - nodeA.x
                val mapDy = nodeB.y - nodeA.y
                val angleMap = kotlin.math.atan2(mapDy, mapDx)

                // Tính góc lệch
                var diff = angleMap - angleAR
                while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
                while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

                // Cache lại góc xoay
                cachedDynamicRotation = Math.toDegrees(diff.toDouble()).toFloat()
            }
        }

        // Gọi hàm tính toán (Nó sẽ dùng referenceNodeId mới nhất = closestId)
        return calculateMapCoordinateFromPose(cameraPose)
    }

    private fun calculateMapCoordinateFromPose(
        targetPose: Pose,
        rotationOverride: Float? = null
    ): Offset? {
        val refId = referenceNodeId ?: return null
        // [QUAN TRỌNG] Lấy Anchor Sống từ Map, KHÔNG dùng biến lưu Pose cũ
        val refAnchor = nodesAndAnchor[refId] ?: return null
        val refNode = allNodes.find { it.id == refId } ?: return null

        // 1. Tính delta World (Target - Reference)
        // refAnchor.pose luôn trả về tọa độ mới nhất của Anchor trong thế giới thực
        val dx = targetPose.tx() - refAnchor.pose.tx()
        val dz = targetPose.tz() - refAnchor.pose.tz()

        // 2. Xác định góc xoay
        val rotationToUse = rotationOverride ?: cachedDynamicRotation ?: 0f
        val rad = Math.toRadians(rotationToUse.toDouble())
        val sinT = kotlin.math.sin(rad)
        val cosT = kotlin.math.cos(rad)

        // 3. Xoay vector
        val rotatedDx = dx * cosT - dz * sinT
        val rotatedDz = dx * sinT + dz * cosT

        // 4. Map & Scale
        val scale = 150f
        return Offset(
            refNode.x + (rotatedDx * scale).toFloat(),
            refNode.y + (rotatedDz * scale).toFloat()
        )
    }

//    private fun calculateMapCoordinateFromPose(
//        targetPose: Pose,
//        rotationOverride: Float? = null // Tham số mới
//    ): Offset? {
//        val (refCloudId, refAnchorPose) = referenceAnchorPose ?: return null
//        val refNode = allNodes.find { it.cloudAnchorId == refCloudId } ?: return null
//
//        // 1. Tính delta World
//        val dx = targetPose.tx() - refAnchorPose.tx()
//        val dz = targetPose.tz() - refAnchorPose.tz()
//
//        // 2. Xác định góc xoay cần dùng
//        // Ưu tiên: Góc truyền vào -> Góc đã cache -> 0
//        val rotationToUse = rotationOverride ?: cachedDynamicRotation ?: 0f
//
//        val rad = Math.toRadians(rotationToUse.toDouble())
//        val sinT = kotlin.math.sin(rad)
//        val cosT = kotlin.math.cos(rad)
//
//        // 3. Xoay
//        val rotatedDx = dx * cosT - dz * sinT
//        val rotatedDz = dx * sinT + dz * cosT
//
//        // 4. Map & Scale
//        val scale = 150f
//        return Offset(
//            refNode.x + (rotatedDx * scale).toFloat(),
//            refNode.y + (rotatedDz * scale).toFloat()
//        )
//    }

    fun onSceneTouched(
        arSceneView: ArSceneView,
        transformationSystem: TransformationSystem,
        pose: Pose,
        type: String,
        name: String?,
        description: String?,
        vendorName: String?,
        vendorDescription: String?,
        floor: Floor,
        building: Building,
        venueId: Long
    ) {
        val model = sessionManager.modelRenderable ?: return
        val hallway = sessionManager.hallwayRenderable ?: return
        val session = arSceneView.session ?: return

        val localAnchor = session.createAnchor(pose)
        placeObject(
            arSceneView,
            localAnchor,
            if (type == Node.HALLWAY) hallway else model,
            transformationSystem
        )

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = "Đang đồng bộ Cloud...") }
            try {
                val cloudId = hostCloudAnchor(localAnchor)
                if (cloudId != null) {
                    if (allNodes.size == 1 && referenceNodeId != null) {
                        val refAnchor = nodesAndAnchor[referenceNodeId!!]
                        if (refAnchor != null) {
                            autoCalculateMapRotation(refAnchor.pose, pose)
                        }
                    }

                    // Tính tọa độ Map
                    var pos = calculateMapCoordinateFromPose(pose)
                    if (pos == null) {
                        pos = Offset(0f, 0f)
                        cachedDynamicRotation = 0f
                    }
                    val newNode = Node(
                        venueId = venueId,
                        floorId = floor.id,
                        x = pos.x,
                        y = pos.y,
                        type = type,
                        cloudAnchorId = cloudId
                    )
                    val nodeId = mapRepository.upsertNode(newNode)

                    when (type) {
                        Node.BOOTH -> {
                            val vendorId = if (vendorName != null && vendorDescription != null) {
                                infoRepository.upsertVendor(
                                    Vendor(
                                        venueId = venueId,
                                        name = vendorName,
                                        description = vendorDescription
                                    )
                                )
                            } else 0
                            if (name != null && description != null) {
                                val shapeId = mapRepository.upsertShape(
                                    Shape(
                                        venueId = venueId,
                                        nodeId = nodeId,
                                        centerX = pos.x,
                                        centerY = pos.y,
                                        width = 120f,
                                        height = 80f,
                                        label = name,
                                        shape = Shape.Companion.ShapeType.RECTANGLE,
                                        color = 0xFF3B82F6
                                    )
                                )
                                infoRepository.upsertBooth(
                                    Booth(
                                        nodeId = nodeId,
                                        shapeId = shapeId,
                                        vendorId = vendorId,
                                        floorId = floor.id,
                                        buildingId = building.id,
                                        venueId = venueId,
                                        name = name,
                                        description = description
                                    )
                                )
                            }
                        }

                        Node.ROOM -> {
                            if (name != null)
                                mapRepository.upsertShape(
                                    Shape(
                                        venueId = venueId,
                                        nodeId = nodeId,
                                        centerX = pos.x,
                                        centerY = pos.y,
                                        width = 120f,
                                        height = 80f,
                                        label = name,
                                        shape = Shape.Companion.ShapeType.RECTANGLE,
                                        color = 0xFF3B82F6
                                    )
                                )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        resolvedNodeIds.add(nodeId)
                        nodesAndAnchor[nodeId] = localAnchor
                        // Cập nhật tham chiếu cho node mới vừa tạo
                        if (referenceNodeId == null) updateReferenceAnchor(nodeId)
                        _uiState.update { it.copy(loading = false, message = "Lưu thành công!") }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        localAnchor.detach(); _uiState.update {
                            it.copy(
                                loading = false,
                                message = "Lỗi lưu"
                            )
                        }
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
                    when (state) {
                        Anchor.CloudAnchorState.SUCCESS -> cont.resume(cloudId, null)
                        Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {}
                        else -> cont.resume(null, null)
                    }
                }
            } catch (e: Exception) {
                cont.resume(null, null)
            }
        }
    }

    private fun placeObject(
        arSceneView: ArSceneView,
        anchor: Anchor,
        model: ModelRenderable,
        transformationSystem: TransformationSystem
    ) {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arSceneView.scene)
        val modelNode = TransformableNode(transformationSystem)
        modelNode.setParent(anchorNode)
        modelNode.renderable = model
        modelNode.select()
    }

    fun drawPath(arSceneView: ArSceneView, pathNode: List<Node>) {
        val frame = arSceneView.arFrame ?: return
        if (pathNode.isEmpty()) {
            showMessage("Không tìm thấy đường đi.")
            return
        }
        val pathRenderable = sessionManager.pathRenderable
        if (pathRenderable == null) {
            showMessage("Đang tải tài nguyên vẽ đường...")
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            clearPathLines()
            val cameraPose = frame.camera.pose
            val firstNodeId = pathNode.first().id
            val anchorFirst = nodesAndAnchor[firstNodeId]

            if (anchorFirst != null) {
                val camPos = Vector3(cameraPose.tx(), anchorFirst.pose.ty(), cameraPose.tz())
                val firstNodePos =
                    Vector3(anchorFirst.pose.tx(), anchorFirst.pose.ty(), anchorFirst.pose.tz())
                drawLine(arSceneView, camPos, firstNodePos, pathRenderable)
            } else {
                showMessage("Hãy quét xung quanh điểm xuất phát để định vị.")
            }

            for (i in 0 until pathNode.size - 1) {
                val nodeStart = pathNode[i]
                val nodeEnd = pathNode[i + 1]
                val startAnchor = nodesAndAnchor[nodeStart.id]
                val endAnchor = nodesAndAnchor[nodeEnd.id]
                if (startAnchor != null && endAnchor != null) {
                    val p1 =
                        Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
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

    override fun onCleared() {
        super.onCleared()

        // Đóng Session để giải phóng Camera cho các app khác
        try {
            sessionManager.destroySession()
            Log.d("ArViewModel", "Đã dọn dẹp Session và Camera")
        } catch (e: Exception) {
            Log.e("ArViewModel", "Lỗi khi đóng session: ${e.message}")
        }
    }
}
