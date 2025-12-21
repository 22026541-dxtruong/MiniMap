package ie.app.minimap.ui.ar

import android.app.Application
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlinx.coroutines.future.await

class ArSessionManager(
    private val application: Application
) {
    private val TAG = "ArSessionManager"

    var arSession: Session? = null
        private set

    var modelRenderable: ModelRenderable? = null
        private set
    var hallwayRenderable: ModelRenderable? = null
        private set
    var pathRenderable: ModelRenderable? = null
        private set

    private var isModelsLoading = false

    fun getOrCreateSession(): Session {
        if (arSession == null) {
            Log.d(TAG, "Creating new AR Session")
            try {
                arSession = Session(application).apply {
                    val config = Config(this).apply {
                        // Tối ưu hóa: Lấy ảnh mới nhất thay vì đợi (giảm lag camera)
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        
                        // Tối ưu hóa: Chỉ quét mặt phẳng ngang để giảm tải CPU
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        
                        // Tối ưu hóa: Tắt ước lượng ánh sáng nếu không cần đổ bóng thực tế
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                        
                        cloudAnchorMode = Config.CloudAnchorMode.ENABLED
                        focusMode = Config.FocusMode.AUTO
                    }
                    this.configure(config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating AR Session: ${e.message}")
                throw e
            }
        }
        return arSession!!
    }

    suspend fun loadModels() {
        if (modelRenderable != null || isModelsLoading) return
        isModelsLoading = true
        try {
            Log.d(TAG, "Loading 3D Models in Manager")
            // Sử dụng màu sắc đơn giản để tiết kiệm GPU
            val materialRed = MaterialFactory.makeOpaqueWithColor(application, Color(android.graphics.Color.RED)).await()
            val materialBlue = MaterialFactory.makeOpaqueWithColor(application, Color(android.graphics.Color.BLUE)).await()
            val materialGreen = MaterialFactory.makeOpaqueWithColor(application, Color(android.graphics.Color.GREEN)).await()

            modelRenderable = ShapeFactory.makeCube(Vector3(0.12f, 0.12f, 0.12f), Vector3(0.0f, 0.06f, 0.0f), materialRed)
            hallwayRenderable = ShapeFactory.makeCube(Vector3(0.1f, 0.1f, 0.1f), Vector3(0.0f, 0.05f, 0.0f), materialGreen)
            
            // Giảm độ chi tiết của Sphere bằng cách dùng Cube nhỏ hoặc Sphere ít mặt (nếu Sceneform cho phép)
            // Ở đây giữ Sphere nhưng dùng kích thước nhỏ
            pathRenderable = ShapeFactory.makeSphere(0.04f, Vector3.zero(), materialBlue)
            
            Log.d(TAG, "Models loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models: ${e.message}")
            throw e
        } finally {
            isModelsLoading = false
        }
    }

    fun clearRenderables() {
        modelRenderable = null
        hallwayRenderable = null
        pathRenderable = null
    }

    fun destroySession() {
        Log.d(TAG, "Destroying AR Session")
        arSession?.close()
        arSession = null
    }
}
