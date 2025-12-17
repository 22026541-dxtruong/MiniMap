package ie.app.minimap.ui.ar

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.*
import ie.app.minimap.data.local.entity.Shape
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArUiState(
    val transformationSystem: TransformationSystem? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isLocalized: Boolean = false // <--- TRẠNG THÁI MỚI
)

@HiltViewModel
class ArViewModel @Inject constructor(
    private val application: Application,
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {

    private val TAG = "ArViewModelLog"

    private val _uiState = MutableStateFlow(ArUiState(loading = true))
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    private var arSession: Session? = null
    private var modelRenderable: ModelRenderable? = null
    private var pathRenderable: ModelRenderable? = null

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
        loadModel()
    }

    // --- HÀM MỚI: XÓA MESSAGE SAU KHI UI ĐÃ HIỆN ---
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // --- HÀM MỚI: SET MESSAGE TIỆN LỢI ---
    private fun showMessage(msg: String) {
        _uiState.update { it.copy(message = msg) }
    }

    private fun loadModel() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Bắt đầu tải Model 3D...")
                _uiState.update { it.copy(loading = true) }
                val materialRed = MaterialFactory.makeOpaqueWithColor(
                    application,
                    Color(android.graphics.Color.RED)
                ).await()
                val materialBlue = MaterialFactory.makeOpaqueWithColor(
                    application,
                    Color(android.graphics.Color.BLUE)
                ).await()

                modelRenderable = ShapeFactory.makeCube(
                    Vector3(0.1f, 0.1f, 0.1f),
                    Vector3(0.0f, 0.05f, 0.0f),
                    materialRed
                )
                pathRenderable = ShapeFactory.makeSphere(0.05f, Vector3.zero(), materialBlue)

                Log.d(TAG, "Tải Model 3D thành công!")
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tải Model 3D: ${e.message}")
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
        Log.d(TAG, "Sync dữ liệu: ${newNodes.size} nodes")

        // 1. Cập nhật danh sách nguồn
        allNodes = newNodes

        // 2. TÌM VÀ DIỆT (Dọn dẹp các Anchor không còn tồn tại trong DB)

        // Lấy danh sách ID hợp lệ từ DB
        val validIds = newNodes.map { it.id }.toSet()

        // Lấy danh sách ID đang lưu trong bộ nhớ Cache (nodesAndAnchor)
        // (Phải toSet() để tạo bản sao, tránh lỗi ConcurrentModification khi xóa loop)
        val currentCachedIds = nodesAndAnchor.keys.toSet()

        // Tìm những ID "mồ côi" (Có trong Cache nhưng ko có trong DB)
        val deletedIds = currentCachedIds.filter { it !in validIds }

        if (deletedIds.isNotEmpty()) {
            Log.i(TAG, "🧹 Phát hiện ${deletedIds.size} node đã bị xóa. Đang dọn dẹp AR...")

            deletedIds.forEach { id ->
                // A. Detach khỏi ARCore để ngừng tracking/render
                nodesAndAnchor[id]?.detach()

                // B. Xóa khỏi bộ nhớ đệm
                nodesAndAnchor.remove(id)
                resolvedNodeIds.remove(id)
                resolvingNodeIds.remove(id)
            }
        }

        // 3. KIỂM TRA REFERENCE ANCHOR (MỐC)
        // Nếu cái Node đang làm Mốc bị xóa mất -> Phải reset để tìm Mốc mới
        if (referenceAnchorPose != null) {
            val (refCloudId, _) = referenceAnchorPose!!
            // Kiểm tra xem cloudId của mốc có còn nằm trong danh sách node mới không
            val isRefStillValid = newNodes.any { it.cloudAnchorId == refCloudId }

            if (!isRefStillValid) {
                Log.w(TAG, "⚠️ Node Mốc đã bị xóa khỏi DB! Reset hệ thống để tìm Mốc mới.")
                referenceAnchorPose = null
                updateLocalizationState()

                // Reset lại batch để quét lại từ đầu
                currentBatchIndex = 0
                lastBatchTime = 0L
            }
        }

        // 4. Reset batch index nếu cần (Logic cũ)
        if (newNodes.isEmpty()) {
            currentBatchIndex = 0
            lastBatchTime = 0L
            showMessage("Không có dữ liệu điểm mốc.")
        }
    }

    fun onResume(context: Context, arSceneView: ArSceneView) {
        Log.d(TAG, "OnResume")
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
            if (arSession == null) arSession = createArSession(context)
            if (arSceneView.session == null) arSceneView.setupSession(arSession)
            arSceneView.resume()
            Log.d(TAG, "AR Session Resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi OnResume: ${e.message}")
            _uiState.update {
                it.copy(
                    error = e.message ?: "Lỗi AR",
                    message = "Không thể khởi động Camera AR. Hãy kiểm tra quyền truy cập."
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

        // --- LOGIC MỚI: TÌM REFERENCE ANCHOR DỰA TRÊN TRẠNG THÁI TRACKING ---
        if (referenceAnchorPose == null) {
            // Duyệt qua tất cả các Anchor đã tải về (Resolved)
            for ((nodeId, anchor) in nodesAndAnchor) {
                // Chỉ lấy cái nào thực sự đang được Camera nhìn thấy (TRACKING)
                if (anchor.trackingState == TrackingState.TRACKING) {
                    val node = allNodes.find { it.id == nodeId }
                    if (node != null) {
                        referenceAnchorPose = node.cloudAnchorId to anchor.pose
//                        Log.i(TAG, "🎯 ĐÃ KHÓA MỐC (TRACKING): ${node.label}")
//                        showMessage("Đã định vị theo: ${node.label}")
                        updateLocalizationState()

                        // Break ngay để lấy cái đầu tiên track được
                        break
                    }
                }
            }
        }

        // TRƯỜNG HỢP 1: CHƯA ĐỊNH VỊ (Mò đường)
        if (referenceAnchorPose == null) {
            val currentTime = System.currentTimeMillis()
            if (allNodes.size > BATCH_SIZE) {
                if (currentTime - lastBatchTime > BATCH_DURATION) {
                    rotateToNextBatch(arSceneView)
                    lastBatchTime = currentTime
                }
            } else {
                // Nếu ít node, gọi 1 lần đầu tiên
                if (resolvingNodeIds.isEmpty() && resolvedNodeIds.isEmpty()) {
                    rotateToNextBatch(arSceneView)
                }
            }
        }
        // TRƯỜNG HỢP 2: ĐÃ ĐỊNH VỊ (Quét gần)
        else {
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

        // Log.i(TAG, "🔄 Quét Batch [$startIndex - $endIndex]")
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
//                        Log.i(TAG, "✅ THÀNH CÔNG: ${node.label}")
                        resolvingNodeIds.remove(node.id)
                        resolvedNodeIds.add(node.id)
                        nodesAndAnchor[node.id] = anchor

                        val system = _uiState.value.transformationSystem
                        if (system != null && modelRenderable != null) {
                            placeObject(arSceneView, anchor, modelRenderable!!, system)
                        }
                    }

                    Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {}
                    Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND -> {
//                        Log.e(TAG, "❌ ID không tồn tại: ${node.label}")
                        // showMessage("Lỗi dữ liệu: Không tìm thấy ID của ${node.label}")
                        resolvingNodeIds.remove(node.id)
                    }

                    else -> {
                        resolvingNodeIds.remove(node.id)
                    }
                }
            }
        } catch (_: Exception) {
            resolvingNodeIds.remove(node.id)
        }
    }

    // ... (Giữ nguyên các hàm tính toán vị trí, createSession, distance...) ...
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

    fun onDestroy(arSceneView: ArSceneView) {
        arSceneView.pause()
        arSceneView.session?.close()
        arSceneView.destroy()
        arSession = null
        _uiState.update { it.copy(transformationSystem = null) }
    }

    fun worldToCanvas(x: Float, y: Float, scaleFactor: Float = 150f) =
        Offset(x * scaleFactor, y * scaleFactor)

    // Sửa tham số đầu vào: Nhận Pose thay vì Float rời rạc
    fun updateUserLocationFromWorld(cameraPose: Pose): Offset? {
        if (referenceAnchorPose == null) return null
        val (refCloudId, refAnchorPose) = referenceAnchorPose!!

        val refNode = allNodes.firstOrNull { it.cloudAnchorId == refCloudId } ?: return null

        // --- BƯỚC QUAN TRỌNG: CHUYỂN ĐỔI HỆ TỌA ĐỘ ---

        // refAnchorPose.inverse().compose(cameraPose) nghĩa là:
        // "Vị trí của Camera đang ở đâu NẾU xem Anchor là gốc tọa độ (0,0,0)?"
        // Hàm này tự động xử lý cả việc trừ tọa độ (Translation) VÀ Xoay (Rotation).
        val relativePose = refAnchorPose.inverse().compose(cameraPose)

        // relativePose.tx(): Khoảng cách Trái/Phải so với Anchor
        // relativePose.tz(): Khoảng cách Trước/Sau so với Anchor
        // Lưu ý: Trong ARCore, -Z là phía trước, +X là bên phải.
        val dx = relativePose.tx()
        val dz = relativePose.tz()

        // --- MAP VÀO CANVAS ---
        // Lúc này dx, dz là khoảng cách mét so với cái Anchor.
        // Ta cộng vào tọa độ gốc của Node trên bản đồ.

        // LƯU Ý QUAN TRỌNG:
        // Điều này giả định lúc bạn HOST Anchor, bạn đã đứng quay lưng vào Anchor
        // và hướng điện thoại cùng chiều với trục của bản đồ.

        return worldToCanvas(dx, dz) + Offset(refNode.x, refNode.y)
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
        val model = modelRenderable ?: return
        val session = arSceneView.session ?: return

        // 1. Hiển thị vật thể ảo ngay lập tức (Local)
        val localAnchor = session.createAnchor(pose)
        placeObject(arSceneView, localAnchor, model, currentState.transformationSystem)

        viewModelScope.launch {
            // Bật Loading + Message
            _uiState.update { it.copy(loading = true, message = "Đang đồng bộ Cloud...") }

            try {
                // 2. Host Cloud Anchor
                val cloudId = hostCloudAnchor(localAnchor)

                if (cloudId != null) {
                    // --- THÀNH CÔNG ---
                    val pos = worldToCanvas(pose.tx(), pose.tz())
                    val newNode = Node(
                        venueId = venueId,
                        floorId = floor.id,
                        x = pos.x,
                        y = pos.y,
//                        label = name ?: "Node ${cloudId.take(4)}",
                        type = type,
                        cloudAnchorId = cloudId
                    )

                    // Lưu DB
                    val nodeId = mapRepository.upsertNode(newNode)

                    when (type) {
                        Node.BOOTH -> {
                            val vendorId = if (vendorName != null && vendorDescription != null) {
                                // Đảm bảo vendor được insert thành công trước khi lấy ID
                                val newVendor = Vendor(
                                    venueId = venueId,
                                    name = vendorName,
                                    description = vendorDescription
                                )
                                val insertedVendorId =
                                    infoRepository.upsertVendor(newVendor) // Chắc chắn lấy vendorId hợp lệ
                                insertedVendorId // Trả về vendorId hợp lệ
                            } else 0
                            if (name != null && description != null) {
                                val shapeId = mapRepository.upsertShape(
                                    Shape(
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

                    // Chặn trùng lặp Resolve
                    withContext(Dispatchers.Main) {
                        resolvedNodeIds.add(nodeId)
                        nodesAndAnchor[nodeId] = localAnchor

                        if (referenceAnchorPose == null) {
                            referenceAnchorPose = cloudId to localAnchor.pose
                            updateLocalizationState()
                        }

                        // TẮT LOADING + THÔNG BÁO THÀNH CÔNG
                        _uiState.update {
                            it.copy(
                                loading = false,
                                message = "✅ Đã lưu thành công!" // Message này sẽ hiện lên Snackbar
                            )
                        }
                    }
                } else {
                    // --- THẤT BẠI KHI HOST ---
                    withContext(Dispatchers.Main) {
                        localAnchor.detach() // Xóa vật thể ảo
                        _uiState.update {
                            it.copy(
                                loading = false,
                                message = "❌ Không thể lưu lên Cloud. Hãy thử lại."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // --- LỖI NGOẠI LỆ ---
                Log.e(TAG, "Lỗi onSceneTouched: ${e.message}")
                withContext(Dispatchers.Main) {
                    localAnchor.detach()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null, // Đừng set error ở đây kẻo nó hiện màn hình đỏ chết chóc
                            message = "Lỗi: ${e.message}" // Hiện snackbar thôi
                        )
                    }
                }
            }
        }
    }

    // ... (Giữ nguyên hostCloudAnchor và createArSession) ...
    private suspend fun hostCloudAnchor(localAnchor: Anchor): String? {
        return suspendCancellableCoroutine { cont ->
            val session = arSession ?: return@suspendCancellableCoroutine
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

    private fun createArSession(context: Context): Session {
        return try {
            Session(context).apply {
                val config = Config(this).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    // depthMode = Config.DepthMode.AUTOMATIC // Bật nếu cần
                    cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                    focusMode = Config.FocusMode.AUTO
                }
                this.configure(config)
            }
        } catch (e: Exception) {
            throw Exception("Lỗi khởi tạo ARCore: ${e.message}")
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
            showMessage("Không tìm thấy đường đi.")
            return
        }
        if (pathRenderable == null) {
            showMessage("Đang tải tài nguyên vẽ đường...")
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            clearPathLines()
            val cameraPose = frame.camera.pose

            // Tìm điểm đầu
            val firstNodeId = pathNode.first().id
            val anchorFirst = nodesAndAnchor[firstNodeId]

            if (anchorFirst != null) {
                val camPos = Vector3(cameraPose.tx(), anchorFirst.pose.ty(), cameraPose.tz())
                val firstNodePos =
                    Vector3(anchorFirst.pose.tx(), anchorFirst.pose.ty(), anchorFirst.pose.tz())
                drawLine(arSceneView, camPos, firstNodePos)

                // showMessage("Bắt đầu dẫn đường...")
            } else {
                Log.w(TAG, "Chưa tìm thấy Anchor đầu tiên")
                showMessage("Hãy quét xung quanh điểm xuất phát để định vị.")
            }

            // Vẽ các đoạn tiếp theo...
            for (i in 0 until pathNode.size - 1) {
                val nodeStart = pathNode[i]
                val nodeEnd = pathNode[i + 1]
                val startAnchor = nodesAndAnchor[nodeStart.id]
                val endAnchor = nodesAndAnchor[nodeEnd.id]
                if (startAnchor != null && endAnchor != null) {
                    val p1 =
                        Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
                    val p2 = Vector3(endAnchor.pose.tx(), endAnchor.pose.ty(), endAnchor.pose.tz())
                    drawLine(arSceneView, p1, p2)
                }
            }
        }
    }

    private fun drawLine(arSceneView: ArSceneView, point1: Vector3, point2: Vector3) {
        val scene = arSceneView.scene ?: return
        val difference = Vector3.subtract(point2, point1)
        val stepSize = 0.15f
        val steps = (difference.length() / stepSize).toInt()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val position = Vector3.add(point1, difference.scaled(t))
            val node = com.google.ar.sceneform.Node().apply {
                setParent(scene)
                renderable = pathRenderable
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