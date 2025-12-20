package ie.app.minimap.ui.ar

import android.app.Application
import android.content.Context
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
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
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
    val transformationSystem: TransformationSystem? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isLocalized: Boolean = false
)

@HiltViewModel
class ArViewModel @Inject constructor(
    private val application: Application,
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository,
    private val sessionManager: ArSessionManager // Inject Singleton Manager
) : ViewModel() {

    private val TAG = "ArViewModelLog"

    private val _uiState = MutableStateFlow(ArUiState(loading = true))
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    private var referenceAnchorPose: Pair<String, Pose>? = null

    private val nodesAndAnchor: MutableMap<Long, Anchor> = mutableMapOf()
    private val resolvedNodeIds = mutableSetOf<Long>()
    private val resolvingNodeIds = mutableSetOf<Long>()

    private var allNodes: List<Node> = emptyList()
    private val pathLines = mutableListOf<com.google.ar.sceneform.Node>()

    private var currentBatchIndex = 0
    private var lastBatchTime = 0L
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
                sessionManager.loadModels(application)
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

        if (referenceAnchorPose != null) {
            val (refCloudId, _) = referenceAnchorPose!!
            val isRefStillValid = newNodes.any { it.cloudAnchorId == refCloudId }
            if (!isRefStillValid) {
                referenceAnchorPose = null
                updateLocalizationState()
                currentBatchIndex = 0
                lastBatchTime = 0L
            }
        }

        if (newNodes.isEmpty()) {
            currentBatchIndex = 0
            lastBatchTime = 0L
            showMessage("Không có dữ liệu điểm mốc.")
        }
    }

    fun onResume(context: Context, arSceneView: ArSceneView) {
        if (_uiState.value.transformationSystem == null) {
            _uiState.update {
                it.copy(
                    transformationSystem = TransformationSystem(
                        context.resources.displayMetrics,
                        FootprintSelectionVisualizer()
                    )
                )
            }
        }

        try {
            val session = sessionManager.getOrCreateSession(context)
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
            it.copy(isLocalized = referenceAnchorPose != null)
        }
    }

    fun onUpdate(arSceneView: ArSceneView) {
        val currentState = _uiState.value
        if (currentState.loading) return
        if (allNodes.isEmpty()) return

        val arFrame = arSceneView.arFrame ?: return
        val camera = arFrame.camera
        if (camera.trackingState != TrackingState.TRACKING) return

        val cameraPose = camera.pose

        if (referenceAnchorPose == null) {
            for ((nodeId, anchor) in nodesAndAnchor) {
                if (anchor.trackingState == TrackingState.TRACKING) {
                    val node = allNodes.find { it.id == nodeId }
                    if (node != null) {
                        referenceAnchorPose = node.cloudAnchorId to anchor.pose
                        updateLocalizationState()
                        break
                    }
                }
            }
        }

        if (referenceAnchorPose == null) {
            val currentTime = System.currentTimeMillis()
            if (allNodes.size > BATCH_SIZE) {
                if (currentTime - lastBatchTime > BATCH_DURATION) {
                    rotateToNextBatch(arSceneView)
                    lastBatchTime = currentTime
                }
            } else {
                if (resolvingNodeIds.isEmpty() && resolvedNodeIds.isEmpty()) {
                    rotateToNextBatch(arSceneView)
                }
            }
        } else {
            viewModelScope.launch(Dispatchers.Default) {
                allNodes.forEach { node ->
                    if (node.id !in resolvedNodeIds && node.id !in resolvingNodeIds) {
                        val predictedWorldPos = calculatePredictedWorldPosition(node, allNodes)
                        if (predictedWorldPos != null) {
                            val dist = distance(
                                cameraPose.tx(), 0f, cameraPose.tz(),
                                predictedWorldPos.x, 0f, predictedWorldPos.z
                            )
                            if (dist < 8.0f) {
                                resolveNodeIfNotBusy(arSceneView, node)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun rotateToNextBatch(arSceneView: ArSceneView) {
        val totalNodes = allNodes.size
        if (totalNodes == 0) return

        resolvingNodeIds.clear()

        val startIndex = currentBatchIndex
        var endIndex = startIndex + BATCH_SIZE
        if (endIndex > totalNodes) {
            endIndex = totalNodes
            currentBatchIndex = 0
        } else {
            currentBatchIndex += BATCH_SIZE
            if (currentBatchIndex >= totalNodes) currentBatchIndex = 0
        }

        val rotatingNodes = allNodes.subList(startIndex, endIndex)
        val landmarkNodes = allNodes.filter {
            it.type == Node.CONNECTOR || it.type == Node.INTERSECTION
        }.take(5)

        val nodesToScan = (rotatingNodes + landmarkNodes).distinctBy { it.id }

        nodesToScan.forEach { node ->
            resolveNodeIfNotBusy(arSceneView, node)
        }
    }

    private fun resolveNodeIfNotBusy(arSceneView: ArSceneView, node: Node) {
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

                        val system = _uiState.value.transformationSystem
                        val model = sessionManager.modelRenderable
                        if (system != null && model != null && node.type != Node.HALLWAY) {
                            placeObject(arSceneView, anchor, model, system)
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

    private fun calculatePredictedWorldPosition(targetNode: Node, allNodes: List<Node>): Vector3? {
        if (referenceAnchorPose == null) return null
        val (refCloudId, refPose) = referenceAnchorPose!!
        val refNode = allNodes.find { it.cloudAnchorId == refCloudId } ?: return null
        val scaleFactor = 150f
        val dxMap = targetNode.x - refNode.x
        val dyMap = targetNode.y - refNode.y
        return Vector3(refPose.tx() + dxMap / scaleFactor, 0f, refPose.tz() + dyMap / scaleFactor)
    }

    private fun distance(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float =
        kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2))

    fun onPause(arSceneView: ArSceneView) {
        arSceneView.pause()
    }

    fun onDestroyView(arSceneView: ArSceneView) {
        arSceneView.pause()
        // We do NOT close the session here, as it's managed by the Singleton Manager
        arSceneView.destroy()
        _uiState.update { it.copy(transformationSystem = null) }
    }

    fun worldToCanvas(x: Float, y: Float, scaleFactor: Float = 150f) =
        Offset(x * scaleFactor, y * scaleFactor)

    fun updateUserLocationFromWorld(cameraPose: Pose): Offset? {
        val (refCloudId, refAnchorPose) = referenceAnchorPose ?: return null
        val refNode = allNodes.find { it.cloudAnchorId == refCloudId } ?: return null// 1. Lấy Pose tương đối của Camera so với Anchor tham chiếu
        // Phép tính này đưa camera về không gian cục bộ của Anchor (Anchor-space)
        val relativePose = refAnchorPose.inverse().compose(cameraPose)

        // Trong Anchor-space:
        // tx: di chuyển sang phải/trái so với hướng Anchor lúc host
        // tz: di chuyển tiến/lùi so với hướng Anchor lúc host
        val tx = relativePose.tx()
        val tz = relativePose.tz()

        // 2. Chuyển đổi sang hệ tọa độ Canvas (X, Y)
        // Giả định: 1m trong AR = 150 pixel trên Canvas
        val scale = 150f

        // QUAN TRỌNG: Chúng ta map tx -> X và tz -> Y
        // Nếu bạn thấy đi tiến mà chấm đi xuống, hãy đổi thành -tz * scale
        val mapX = refNode.x + (tx * scale)
        val mapY = refNode.y + (tz * scale)

        return Offset(mapX, mapY)
    }

    fun onSceneTouched(
        arSceneView: ArSceneView,
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
        val currentState = _uiState.value
        if (currentState.transformationSystem == null) return
        val model = sessionManager.modelRenderable ?: return
        val hallway = sessionManager.hallwayRenderable ?: return
        val session = arSceneView.session ?: return

        val localAnchor = session.createAnchor(pose)
        placeObject(arSceneView, localAnchor, if (type == Node.HALLWAY) hallway else model,
            currentState.transformationSystem
        )

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = "Đang đồng bộ Cloud...") }
            try {
                val cloudId = hostCloudAnchor(localAnchor)
                if (cloudId != null) {
                    val pos = worldToCanvas(pose.tx(), pose.tz())
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
                                infoRepository.upsertVendor(Vendor(venueId = venueId, name = vendorName, description = vendorDescription))
                            } else 0
                            if (name != null && description != null) {
                                val shapeId = mapRepository.upsertShape(
                                    Shape(nodeId = nodeId, centerX = pos.x, centerY = pos.y, width = 120f, height = 80f, label = name, shape = Shape.Companion.ShapeType.RECTANGLE, color = 0xFF3B82F6)
                                )
                                infoRepository.upsertBooth(
                                    Booth(nodeId = nodeId, shapeId = shapeId, vendorId = vendorId, floorId = floor.id, buildingId = building.id, venueId = venueId, name = name, description = description)
                                )
                            }
                        }
                        Node.ROOM -> {
                            if (name != null)
                                mapRepository.upsertShape(
                                    Shape(nodeId = nodeId, centerX = pos.x, centerY = pos.y, width = 120f, height = 80f, label = name, shape = Shape.Companion.ShapeType.RECTANGLE, color = 0xFF3B82F6)
                                )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        resolvedNodeIds.add(nodeId)
                        nodesAndAnchor[nodeId] = localAnchor
                        if (referenceAnchorPose == null) {
                            referenceAnchorPose = cloudId to localAnchor.pose
                            updateLocalizationState()
                        }
                        _uiState.update { it.copy(loading = false, message = "✅ Đã lưu thành công!") }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        localAnchor.detach()
                        _uiState.update { it.copy(loading = false, message = "❌ Không thể lưu lên Cloud. Hãy thử lại.") }
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

    fun drawPath(arSceneView: ArSceneView, pathNode: List<Node>?) {
        val frame = arSceneView.arFrame ?: return
        if (pathNode.isNullOrEmpty()) {
            Log.d(TAG, "Không tìm thấy đường đi.")
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
                val firstNodePos = Vector3(anchorFirst.pose.tx(), anchorFirst.pose.ty(), anchorFirst.pose.tz())
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
                    val p1 = Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
                    val p2 = Vector3(endAnchor.pose.tx(), endAnchor.pose.ty(), endAnchor.pose.tz())
                    drawLine(arSceneView, p1, p2, pathRenderable)
                }
            }
        }
    }

    private fun drawLine(arSceneView: ArSceneView, point1: Vector3, point2: Vector3, renderable: ModelRenderable) {
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
