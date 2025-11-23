package ie.app.minimap.ui.ar

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
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
import kotlin.math.sqrt

data class ArUiState(
    val transformationSystem: TransformationSystem? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArViewModel @Inject constructor(
    private val application: Application,
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArUiState(loading = true))
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    //node chua duoc host
    private val _hostNode = MutableStateFlow<Pair<Anchor, Node>?>(null)
    val hostNode: StateFlow<Pair<Anchor, Node>?> = _hostNode.asStateFlow()

    // Biến nội bộ của ViewModel
    private var arSession: Session? = null
    private var modelRenderable: ModelRenderable? = null
    private var pathRenderable: ModelRenderable? = null

    private var referenceAnchorPose: Pair<String, Pose>? = null

    private val nodesAndAnchor: MutableMap<Long, Anchor> = mutableMapOf()

    private var tempCloudIds: List<Node> = emptyList()
    private val pathLines = mutableListOf<com.google.ar.sceneform.Node>()

    init {
        // Bắt đầu tải mô hình 3D ngay khi ViewModel được tạo
        loadModel()
    }

    /**
     * Tải mô hình 3D bằng coroutine
     */
    private fun loadModel() {
        viewModelScope.launch {
            try {
                // Tải vật liệu và tạo mô hình
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
                pathRenderable = ShapeFactory.makeSphere(
                    0.05f,
                    Vector3.zero(),
                    materialBlue
                )
                // Nếu session đã sẵn sàng, chuyển sang Ready.
                // Nếu chưa, onResume sẽ xử lý.
//                if (_uiState.value is ArUiState.Loading && transformationSystem != null) {
//                    _uiState.value = ArUiState.Ready(transformationSystem!!)
//                }
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Không thể tải mô hình 3D: ${e.message}") }
            }
        }
    }

    /** Nhận danh sách Node từ UI */
    fun updateCloudAnchors(nodes: List<Node>) {
        tempCloudIds = nodes
    }

    /**
     * Composable sẽ gọi hàm này khi có sự kiện ON_RESUME
     */
    fun onResume(context: Context, arSceneView: ArSceneView) {
        // Tạo TransformationSystem một lần duy nhất
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
            if (arSession == null) {
                arSession = createArSession(context)
            }
            if (arSceneView.session == null) {
                // Tạo AR Session (từ logic cũ của bạn)
                arSceneView.setupSession(arSession)
            }
            // Tiếp tục session
            arSceneView.resume()

        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message ?: "Lỗi không xác định khi khởi động AR") }
        }
    }

    /** Xử lý update mỗi frame */
    fun onUpdate(arSceneView: ArSceneView) {
        val currentState = _uiState.value
        val system = currentState.transformationSystem ?: return
        if (currentState.loading || tempCloudIds.isEmpty()) return

        val arFrame = arSceneView.arFrame ?: return
        if (arFrame.camera.trackingState != TrackingState.TRACKING) return

        // Resolve tất cả Cloud Anchors
        tempCloudIds.forEach { node ->
            resolveCloudAnchor(node.cloudAnchorId) { anchor ->
                if (anchor != null && modelRenderable != null) {
                    placeObject(arSceneView, anchor, modelRenderable!!, system)
//                    Log.d("CloudAnchor", "🎉 Anchor resolved: $cloudId")
                    nodesAndAnchor[node.id] = anchor
                }
            }
        }

        tempCloudIds = emptyList() // Clear sau khi resolve
    }

    /**
     * Composable sẽ gọi hàm này khi có sự kiện ON_PAUSE
     */
    fun onPause(arSceneView: ArSceneView) {
        arSceneView.pause()
    }

    /**
     * Composable sẽ gọi hàm này khi bị hủy (ON_DESTROY)
     */
    fun onDestroy(arSceneView: ArSceneView) {
        arSceneView.pause()
        arSceneView.session?.close()
        arSceneView.destroy()
        arSession = null
        _uiState.update { it.copy(transformationSystem = null) }
    }

    fun worldToCanvas(
        x: Float,
        y: Float,
        scaleFactor: Float = 150f
    ): Offset { // Ví dụ: 1m ngoài đời = 100 đơn vị trên map
        return Offset(x * scaleFactor, y * scaleFactor)
    }

    fun updateUserLocationFromWorld(cameraPoseX: Float, cameraPoseZ: Float, nodes: List<Node>): Offset? {
        if (referenceAnchorPose == null) return null
        val refCloudAnchorId = referenceAnchorPose!!.first
        val refPose = referenceAnchorPose!!.second

        val refNode = nodes.firstOrNull { it.cloudAnchorId == refCloudAnchorId }
            ?: return null

        val mapX = refNode.x
        val mapY = refNode.y

        // world displacement (camera - anchor)
        val dx = cameraPoseX - refPose.tx()
        val dz = cameraPoseZ - refPose.tz()

        // scale nếu muốn
        return worldToCanvas(dx, dz) + Offset(mapX, mapY)
    }


    /**
     * Composable gọi khi người dùng chạm vào màn hình
     */
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
        val anchor = session.createAnchor(pose)
        _uiState.update { it.copy(loading = true) }
//        hostedNodes.put(anchor, mapRepository.upsertNode())
        viewModelScope.launch {
            try {
                // Chuyển đổi tọa độ AR (mét) sang tọa độ Map (pixel/đơn vị vẽ)
                // Lưu ý: AR dùng (x, y, z) với y là độ cao. Mặt sàn phẳng là (x, z).
                // Map 2D dùng (x, y).
                // Ta map: AR X -> Map X, AR Z -> Map Y.
                val pos = worldToCanvas(pose.tx(), pose.tz())

                val newNode = Node(
                    floorId = floor.id, // ID của tầng hiện tại
                    x = pos.x,
                    y = pos.y,
                    label = name ?: "Node in floor ${floor.id}",
                    type = type // Loại tạm
                )

                // Lưu vào DB và lấy ID trả về
                val nodeId = mapRepository.upsertNode(newNode)

                // Cập nhật lại Node với ID thực tế (để sau này dùng cho cloud mapping)
                val savedNode = newNode.copy(id = nodeId)
                when (type) {
                    Node.BOOTH -> {
                        val vendorId = if (vendorName != null && vendorDescription != null) {
                            // Đảm bảo vendor được insert thành công trước khi lấy ID
                            val newVendor = Vendor(
                                name = vendorName,
                                description = vendorDescription
                            )
                            val insertedVendorId =
                                infoRepository.upsertVendor(newVendor) // Chắc chắn lấy vendorId hợp lệ
                            insertedVendorId // Trả về vendorId hợp lệ
                        } else 0

                        // Lưu Booth với nodeId và vendorId hợp lệ
                        if (name != null && description != null) {
                            infoRepository.upsertBooth(
                                Booth(
                                    nodeId = nodeId,
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
                }

                _hostNode.value = anchor to savedNode
                nodesAndAnchor[savedNode.id] = anchor

                Log.d(
                    "ArViewModel",
                    "Đã thêm Node vào DB: ID=$nodeId tại (${savedNode.x}, ${savedNode.y})"
                )
                _uiState.update { it.copy(loading = false) }
                // 3. Đặt object vào scene
                placeObject(arSceneView, anchor, model, currentState.transformationSystem)
            } catch (e: Exception) {
                Log.e("ArViewModel", "Lỗi khi thêm Node: ${e.message}")
                _uiState.update { it.copy(error = "Lỗi khi thêm Node: ${e.message}", loading = false) }
            }
        }
    }

    private suspend fun hostCloudAnchor(localAnchor: Anchor): String? {
        return suspendCancellableCoroutine { cont ->
            val session = arSession ?: return@suspendCancellableCoroutine
            try {
                // Host Cloud Anchor với TTL 1 ngày
                session.hostCloudAnchorAsync(localAnchor, 1) { cloudId, state ->
                    when (state) {
                        Anchor.CloudAnchorState.SUCCESS -> {
                            // Hosting thành công, tiếp tục với giá trị cloudId
                            cont.resume(cloudId, onCancellation = { throwable, value, context ->
                                // Xử lý hủy nếu cần thiết, có thể để trống nếu không cần xử lý cancellation
                                Log.e("CloudAnchor", "Hosting bị huỷ: ${throwable?.message}")
                            })
                        }

                        Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {
                            // Không làm gì, chờ callback tiếp theo
                        }

                        else -> {
                            // Hosting thất bại, trả về null
                            cont.resume(null, onCancellation = { throwable, value, context ->
                                // Xử lý hủy nếu cần thiết
                                Log.e(
                                    "CloudAnchor",
                                    "Hosting thất bại và bị huỷ: ${throwable?.message}"
                                )
                            })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CloudAnchor", "Hosting failed: ${e.message}")
                // Nếu có lỗi khi host, trả về null
                cont.resume(null, onCancellation = { throwable, value, context ->
                    // Xử lý hủy khi có lỗi
                    Log.e("CloudAnchor", "Hosting bị huỷ do lỗi: ${throwable?.message}")
                })
            }
        }
    }

    private fun resolveCloudAnchor(cloudAnchorId: String, onResult: (Anchor?) -> Unit) {
        val session = arSession ?: return

        try {
            session.resolveCloudAnchorAsync(cloudAnchorId) { cloudAnchor, state ->
                when (state) {
                    Anchor.CloudAnchorState.SUCCESS -> {
                        Log.d("CloudAnchor", "✅ Cloud Anchor resolved: $cloudAnchorId")
                        Toast.makeText(
                            application,
                            "Cloud Anchor resolved: $cloudAnchorId",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Nếu chưa có anchor tham chiếu → đặt anchor này làm refAnchor
                        if (referenceAnchorPose == null) {
                            referenceAnchorPose = cloudAnchorId to cloudAnchor.pose
                            Log.d("CloudAnchor", "📌 Set reference anchor from $cloudAnchorId")
                        }
                        onResult(cloudAnchor)
                    }

                    Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {
                        Log.d("CloudAnchor", "⏳ Resolving Cloud Anchor in progress: $cloudAnchorId")
                        // Không gọi onResult, chờ callback tiếp
                    }

                    Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
                    Anchor.CloudAnchorState.ERROR_INTERNAL,
                    Anchor.CloudAnchorState.ERROR_SERVICE_UNAVAILABLE,
                    Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED -> {
                        Log.e(
                            "CloudAnchor",
                            "❌ Failed to resolve Cloud Anchor $cloudAnchorId: $state"
                        )
                        onResult(null)
                    }

                    else -> {
                        Log.w("CloudAnchor", "⚠️ Cloud Anchor in unexpected state: $state")
                        // Không gọi onResult
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CloudAnchor", "Resolve failed: ${e.message}")
            Toast.makeText(
                application,
                "Resolve failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            onResult(null)
        }
    }

    /**
     * Logic tạo AR Session (tách ra từ onResume)
     */
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
                Log.d("CloudAnchor", "✅ ARCore đã sẵn sàng")
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            throw Exception("Vui lòng cài đặt Dịch vụ Google Play cho AR")
        } catch (_: UnavailableApkTooOldException) {
            throw Exception("Vui lòng cập nhật Dịch vụ Google Play cho AR")
        } catch (_: UnavailableSdkTooOldException) {
            throw Exception("Vui lòng cập nhật ứng dụng")
        } catch (_: UnavailableDeviceNotCompatibleException) {
            throw Exception("Thiết bị không hỗ trợ AR")
        } catch (_: CameraNotAvailableException) {
            throw Exception("Camera không khả dụng")
        } catch (e: Exception) {
            throw Exception("Lỗi khởi tạo ARCore: ${e.message}")
        }
    }

    /**
     * Logic đặt vật thể (tách ra từ onSceneTouched)
     */
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

    fun exportAnchorToCloud() {
        if (_hostNode.value == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(loading = true) }
                val cloudId = hostCloudAnchor(_hostNode.value!!.first)

                val node = _hostNode.value!!.second
                mapRepository.upsertNode(node.copy(cloudAnchorId = cloudId!!))
//                if (referenceAnchorPose == null) referenceAnchorPose = cloudId to _hostNode.value!!.first.pose
                _hostNode.value = null
                _uiState.update { it.copy(loading = false) }

                // Chờ tất cả anchor được host xong
//            hostedNodes.forEach { node ->
//                val cloudId = hostCloudAnchor(node.key) // Chờ kết quả từ hàm suspend
//                if (cloudId != null) {
//                    jsonList.add(cloudId)
//                    mapRepository.upsertNode(
//                        hostedNodes[node.key]!!.copy(cloudAnchorId = cloudId)
//                    )
//                    Log.d("CloudAnchor", "✅ Cloud Anchor ID: $cloudId")
//                } else {
//                    Log.e("CloudAnchor", "❌ Không thể host Cloud Anchor.")
//                }
//            }

                // Tạo JSON từ danh sách cloudId đã host
//            val jsonList = mutableListOf<String>()
//            val json = """{"anchors": [${jsonList.joinToString(",") { "\"$it\"" }}]}"""
//            Log.d("CloudAnchor", "JSON: $json")
//
//            // Ghi file JSON
//            val file = File(context.getExternalFilesDir(null), "cloud_anchors.json")
//            file.writeText(json)
//
//            Log.d("CloudAnchor", "✅ Đã tạo file JSON tại ${file.absolutePath}")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        "Lỗi khi host Anchor: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Hàm drawPath hoàn thiện
    // Lưu ý: userPoseX/Z hiện tại để Any là chưa đúng, mình sửa thành Float? để dùng nếu cần
    // Hàm vẽ đường đi chính
    fun drawPath(
        arSceneView: ArSceneView,
        pathNode: List<Node>?
    ) {
        val frame = arSceneView.arFrame ?: return
        if (pathNode.isNullOrEmpty()) return

        // Quan trọng: Phải kiểm tra renderable đã load xong chưa
        if (pathRenderable == null) {
            Log.e("ArViewModel", "Path Renderable is null (Model chưa load xong)")
            return
        }

        // Chạy trên Main Thread vì thao tác với SceneView bắt buộc ở UI Thread
        viewModelScope.launch(Dispatchers.Main) {
            // 1. Xóa đường cũ trước khi vẽ đường mới
            clearPathLines()

            val cameraPose = frame.camera.pose

            // --- ĐOẠN 1: Từ Camera -> Node đầu tiên ---
            // Kiểm tra xem Node đầu tiên đã có Anchor thực tế chưa
            val anchorFirst = nodesAndAnchor[pathNode.last().id]

            if (anchorFirst != null) {
                // Vị trí Camera (hạ thấp 0.5m để dây xuất phát từ ngực/bụng người dùng)
                val camPos = Vector3(cameraPose.tx(), cameraPose.ty() - 0.5f, cameraPose.tz())
                // Vị trí Node đầu
                val firstNodePos = Vector3(anchorFirst.pose.tx(), anchorFirst.pose.ty(), anchorFirst.pose.tz())

                drawLine(arSceneView, camPos, firstNodePos)
            } else {
                Log.w("ArViewModel", "Chưa tìm thấy Anchor cho node đầu tiên: ${pathNode[0].label}")
            }

            // --- ĐOẠN 2: Nối các Node với nhau ---
            for (i in 0 until pathNode.size - 1) {
                val nodeStart = pathNode[i]
                val nodeEnd = pathNode[i + 1]

                val startAnchor = nodesAndAnchor[nodeStart.id]
                val endAnchor = nodesAndAnchor[nodeEnd.id]

                // Chỉ vẽ khi CẢ 2 ĐẦU đều đã được resolve (có vị trí thực tế AR)
                if (startAnchor != null && endAnchor != null) {
                    val p1 = Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
                    val p2 = Vector3(endAnchor.pose.tx(), endAnchor.pose.ty(), endAnchor.pose.tz())

                    drawLine(arSceneView, p1, p2)
                }
            }
        }
    }

    // Hàm vẽ đoạn thẳng nối 2 điểm 3D (SỬ DỤNG SCENE NODE, KHÔNG DÙNG ANCHOR)
    private fun drawLine(
        arSceneView: ArSceneView,
        point1: Vector3,
        point2: Vector3
    ) {
        val scene = arSceneView.scene ?: return

        // Tính khoảng cách giữa 2 điểm
        val difference = Vector3.subtract(point2, point1)
        val distance = difference.length()

        // Bước nhảy: Cứ 0.15 mét vẽ 1 chấm
        val stepSize = 0.15f
        val steps = (distance / stepSize).toInt()

        for (i in 0..steps) {
            val t = i.toFloat() / steps

            // Công thức nội suy: Tìm tọa độ nằm giữa point1 và point2
            // Position = p1 + (Vector nối p1->p2) * tỉ lệ t
            val position = Vector3.add(point1, difference.scaled(t))

            // TẠO NODE THƯỜNG (Nhẹ, không tốn tài nguyên tracking)
            val node = com.google.ar.sceneform.Node().apply {
                setParent(scene) // Gắn vào scene
                renderable = pathRenderable
                worldPosition = position // Đặt vị trí
            }

            // Lưu vào list để xóa sau này
            pathLines.add(node)
        }
    }

    // Hàm xóa đường cũ
    private fun clearPathLines() {
        pathLines.forEach { node ->
            node.setParent(null) // Gỡ khỏi scene
        }
        pathLines.clear()
    }

}