package ie.app.minimap.ui.components

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
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
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

sealed interface ArUiState {
    data object Loading : ArUiState // Đang tải mô hình 3D
    data class Ready(val transformationSystem: TransformationSystem) : ArUiState // Sẵn sàng để vẽ
    data class Error(val message: String) : ArUiState // Có lỗi xảy ra
}

@HiltViewModel
class ArViewModel @Inject constructor(
    private val application: Application,
    private val mapRepository: MapRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<ArUiState>(ArUiState.Loading)
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()
    // Biến nội bộ của ViewModel
    private var arSession: Session? = null
    private var modelRenderable: ModelRenderable? = null
    private var transformationSystem: TransformationSystem? = null
    private val hostedNodes = mutableMapOf<Anchor, Node>()
    private var anchorsToResolve = mutableListOf<String>()
    private var hasResolveBeenAttempted = false

    init {
        // Bắt đầu tải mô hình 3D ngay khi ViewModel được tạo
        loadModel()
        loadCloudAnchors()
    }

    /**
     * Tải mô hình 3D bằng coroutine
     */
    private fun loadModel() {
        viewModelScope.launch {
            try {
                // Tải vật liệu và tạo mô hình
                val material = MaterialFactory.makeOpaqueWithColor(
                    application,
                    Color(android.graphics.Color.RED)
                ).await()
                modelRenderable = ShapeFactory.makeCube(
                    Vector3(0.1f, 0.1f, 0.1f),
                    Vector3(0.0f, 0.05f, 0.0f),
                    material
                )
                // Nếu session đã sẵn sàng, chuyển sang Ready.
                // Nếu chưa, onResume sẽ xử lý.
                if (_uiState.value is ArUiState.Loading && transformationSystem != null) {
                    _uiState.value = ArUiState.Ready(transformationSystem!!)
                }
            } catch (e: Exception) {
                _uiState.value = ArUiState.Error("Không thể tải mô hình 3D: ${e.message}")
            }
        }
    }

    private fun loadCloudAnchors() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(application.getExternalFilesDir(null), "cloud_anchors.json")
                if (file.exists()) {
                    val json = file.readText()
                    val cloudIds = JSONObject(json).getJSONArray("anchors")

                    // Xóa danh sách cũ và thêm ID mới
                    anchorsToResolve.clear()
                    for (i in 0 until cloudIds.length()) {
                        anchorsToResolve.add(cloudIds.getString(i))
                    }
                    hasResolveBeenAttempted = false // Đặt lại cờ để thử resolve lại
                    Log.d("CloudAnchor", "✅ Đã tải ${anchorsToResolve.size} anchor ID, sẵn sàng để resolve.")
                }
            } catch (e: Exception) {
                Log.e("CloudAnchor", "❌ Không thể tải danh sách anchors: ${e.message}")
                _uiState.value = ArUiState.Error("Không thể tải danh sách anchors: ${e.message}")
            }
        }
    }

    /**
     * Composable sẽ gọi hàm này khi có sự kiện ON_RESUME
     */
    fun onResume(context: Context, arSceneView: ArSceneView) {
        // Tạo TransformationSystem một lần duy nhất
        if (transformationSystem == null) {
            transformationSystem = TransformationSystem(
                context.resources.displayMetrics,
                FootprintSelectionVisualizer()
            )
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
            // Nếu mô hình đã tải xong, chuyển sang trạng thái Sẵn sàng
            if (modelRenderable != null) {
                _uiState.value = ArUiState.Ready(transformationSystem!!)
            } else {
                _uiState.value = ArUiState.Loading // Vẫn đang tải mô hình
            }
        } catch (e: Exception) {
            _uiState.value = ArUiState.Error(e.message ?: "Lỗi không xác định khi khởi động AR")
        }
    }

    fun onUpdate(arSceneView: ArSceneView) {
        val currentState = _uiState.value
        if (currentState !is ArUiState.Ready || hasResolveBeenAttempted || anchorsToResolve.isEmpty()) {
            return
        }

        // 2. Chỉ chạy khi ARCore đã TRACKING (quan trọng nhất)
        val arFrame = arSceneView.arFrame ?: return
        if (arFrame.camera.trackingState != TrackingState.TRACKING) {
            Log.d("CloudAnchor", "⏳ Đang chờ trạng thái TRACKING...")
            return // Chờ cho đến khi ARCore bắt đầu theo dõi
        }

        // 3. Đánh dấu là đã thử (để không chạy lại 60 lần/giây)
        hasResolveBeenAttempted = true
        Log.d("CloudAnchor", "✅ Session đã TRACKING. Bắt đầu resolve ${anchorsToResolve.size} anchors...")

        // 4. Bắt đầu resolve tất cả
        anchorsToResolve.forEach { cloudId ->
            resolveCloudAnchor(cloudId) { anchor ->
                if (anchor != null) {
                    placeObject(
                        arSceneView,
                        anchor,
                        modelRenderable!!,
                        currentState.transformationSystem
                    )
                    Log.d("CloudAnchor", "🎉 Anchor resolved và hiển thị: $cloudId")
                } else {
                    Log.e("CloudAnchor", "❌ Không resolve được Anchor: $cloudId (từ onFrameUpdate)")
                }
            }
        }
        anchorsToResolve.clear() // Xóa danh sách sau khi đã thử
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
        transformationSystem = null
    }

    fun worldToCanvas(x: Float, y: Float, scaleFactor: Float = 150f): Offset { // Ví dụ: 1m ngoài đời = 100 đơn vị trên map
        return Offset(x * scaleFactor, y * scaleFactor)
    }

    /**
     * Composable gọi khi người dùng chạm vào màn hình
     */
    fun onSceneTouched(
        arSceneView: ArSceneView,
        pose: Pose,
        name: String,
        type: String,
        floorId: Long
    ) {
        val currentState = _uiState.value
        if (currentState !is ArUiState.Ready) return

        val model = modelRenderable ?: return
        val session = arSceneView.session ?: return
        val anchor = session.createAnchor(pose)

        // 3. Đặt object vào scene
        placeObject(arSceneView, anchor, model, currentState.transformationSystem)
//        hostedNodes.put(anchor, mapRepository.upsertNode())
        viewModelScope.launch {
            // Chuyển đổi tọa độ AR (mét) sang tọa độ Map (pixel/đơn vị vẽ)
            // Lưu ý: AR dùng (x, y, z) với y là độ cao. Mặt sàn phẳng là (x, z).
            // Map 2D dùng (x, y).
            // Ta map: AR X -> Map X, AR Z -> Map Y.
            val pos = worldToCanvas(pose.tx(), pose.tz())

            val newNode = Node(
                floorId = floorId, // ID của tầng hiện tại
                x = pos.x,
                y = pos.y,
                label = name, // Tên tạm
                type = type // Loại tạm
            )

            // Lưu vào DB và lấy ID trả về
            val nodeId = mapRepository.upsertNode(newNode)

            // Cập nhật lại Node với ID thực tế (để sau này dùng cho cloud mapping)
            val savedNode = newNode.copy(id = nodeId)

            // Lưu vào map để lát nữa export ra file JSON (Cloud Anchor ID <-> Node ID)
            hostedNodes[anchor] = savedNode

            Log.d("ArViewModel", "Đã thêm Node vào DB: ID=$nodeId tại (${savedNode.x}, ${savedNode.y})")
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
                                Log.e("CloudAnchor", "Hosting thất bại và bị huỷ: ${throwable?.message}")
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
                        Log.e("CloudAnchor", "❌ Failed to resolve Cloud Anchor $cloudAnchorId: $state")
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
        } catch (e: UnavailableUserDeclinedInstallationException) {
            throw Exception("Vui lòng cài đặt Dịch vụ Google Play cho AR")
        } catch (e: UnavailableApkTooOldException) {
            throw Exception("Vui lòng cập nhật Dịch vụ Google Play cho AR")
        } catch (e: UnavailableSdkTooOldException) {
            throw Exception("Vui lòng cập nhật ứng dụng")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            throw Exception("Thiết bị không hỗ trợ AR")
        } catch (e: CameraNotAvailableException) {
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

    fun exportCloudAnchorsToFile(context: Context) {
        if (hostedNodes.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val jsonList = mutableListOf<String>()

            // Chờ tất cả anchor được host xong
            hostedNodes.forEach { node ->
                val cloudId = hostCloudAnchor(node.key) // Chờ kết quả từ hàm suspend
                if (cloudId != null) {
                    jsonList.add(cloudId)
                    mapRepository.upsertNode(
                        hostedNodes[node.key]!!.copy(cloudAnchorId = cloudId)
                    )
                    Log.d("CloudAnchor", "✅ Cloud Anchor ID: $cloudId")
                } else {
                    Log.e("CloudAnchor", "❌ Không thể host Cloud Anchor.")
                }
            }

            // Tạo JSON từ danh sách cloudId đã host
            val json = """{"anchors": [${jsonList.joinToString(",") { "\"$it\"" }}]}"""
            Log.d("CloudAnchor", "JSON: $json")

            // Ghi file JSON
            val file = File(context.getExternalFilesDir(null), "cloud_anchors.json")
            file.writeText(json)

            // Chuyển tiếp thông báo Toast về UI thread
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    application,
                    "Đã host ${jsonList.size} Anchor",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Log.d("CloudAnchor", "✅ Đã tạo file JSON tại ${file.absolutePath}")
        }
    }

}