package ie.app.minimap

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

sealed interface ArUiState {
    data object Loading : ArUiState // Đang tải mô hình 3D
    data class Ready(val transformationSystem: TransformationSystem) : ArUiState // Sẵn sàng để vẽ
    data class Error(val message: String) : ArUiState // Có lỗi xảy ra
}

class MiniMapViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ArUiState>(ArUiState.Loading)
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    // Biến nội bộ của ViewModel
    private var arSession: Session? = null
    private var modelRenderable: ModelRenderable? = null
    private var transformationSystem: TransformationSystem? = null

    init {
        // Bắt đầu tải mô hình 3D ngay khi ViewModel được tạo
        loadModel()
        arSession = createArSession(application)
    }

    /**
     * Tải mô hình 3D bằng coroutine
     */
    private fun loadModel() {
        viewModelScope.launch {
            try {
                // Tải vật liệu và tạo mô hình
                val material = MaterialFactory.makeOpaqueWithColor(application, Color(android.graphics.Color.RED)).await()
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

    /**
     * Composable sẽ gọi hàm này khi có sự kiện ON_RESUME
     */
    fun onResume(context: Context, arSceneView: ArSceneView) {
        // Tạo TransformationSystem một lần duy nhất
        if (transformationSystem == null) {
            transformationSystem = TransformationSystem(context.resources.displayMetrics,
                FootprintSelectionVisualizer()
            )
        }

        try {
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
        transformationSystem = null // Hủy
    }

    /**
     * Composable gọi khi người dùng chạm vào màn hình
     */
    fun onSceneTouched(arSceneView: ArSceneView, hitTestResult: HitTestResult, motionEvent: MotionEvent) {
        val currentState = _uiState.value
        if (currentState !is ArUiState.Ready) return // Chỉ xử lý khi đã sẵn sàng

        // 1. Chuyển sự kiện chạm cho TransformationSystem (để xoay/di chuyển)
        currentState.transformationSystem.onTouch(hitTestResult, motionEvent)

        // 2. Xử lý đặt vật thể
        if (motionEvent.action == MotionEvent.ACTION_UP) {
            val currentModel = modelRenderable ?: return // Model chưa tải xong

            val frame = arSceneView.arFrame ?: return
            val hits = frame.hitTest(motionEvent)
            val hit = hits.firstOrNull {
                val trackable = it.trackable
                (trackable is Plane && trackable.isPoseInPolygon(it.hitPose))
            }

            if (hit != null) {
                placeObject(arSceneView, hit.createAnchor(), currentModel, currentState.transformationSystem)
            }
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
                }
                this.configure(config)
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
}