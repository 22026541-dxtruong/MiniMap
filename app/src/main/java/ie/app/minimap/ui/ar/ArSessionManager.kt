package ie.app.minimap.ui.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlinx.coroutines.future.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArSessionManager @Inject constructor() {
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

    fun getOrCreateSession(context: Context): Session {
        if (arSession == null) {
            Log.d(TAG, "Creating new AR Session")
            try {
                arSession = Session(context).apply {
                    val config = Config(this).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
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

    suspend fun loadModels(context: Context) {
        if (modelRenderable != null || isModelsLoading) return
        isModelsLoading = true
        try {
            Log.d(TAG, "Loading 3D Models in Manager")
            val materialRed = MaterialFactory.makeOpaqueWithColor(
                context,
                Color(android.graphics.Color.RED)
            ).await()
            val materialBlue = MaterialFactory.makeOpaqueWithColor(
                context,
                Color(android.graphics.Color.BLUE)
            ).await()
            val materialGreen = MaterialFactory.makeOpaqueWithColor(
                context,
                Color(android.graphics.Color.GREEN)
            ).await()

            modelRenderable = ShapeFactory.makeCube(
                Vector3(0.1f, 0.1f, 0.1f),
                Vector3(0.0f, 0.05f, 0.0f),
                materialRed
            )
            hallwayRenderable = ShapeFactory.makeCube(
                Vector3(0.1f, 0.1f, 0.1f),
                Vector3(0.0f, 0.05f, 0.0f),
                materialGreen
            )
            pathRenderable = ShapeFactory.makeSphere(0.05f, Vector3.zero(), materialBlue)
            Log.d(TAG, "Models loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models: ${e.message}")
            throw e
        } finally {
            isModelsLoading = false
        }
    }

    fun destroySession() {
        Log.d(TAG, "Destroying AR Session")
        arSession?.close()
        arSession = null
    }
}
