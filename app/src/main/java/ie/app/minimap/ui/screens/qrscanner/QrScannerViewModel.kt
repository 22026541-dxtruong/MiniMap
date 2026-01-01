package ie.app.minimap.ui.screens.qrscanner

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.remote.QrRepository
import ie.app.minimap.ui.qr.QrCodeAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrScannerUiState(
    val venueId: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val infoRepository: InfoRepository,
    private val qrRepository: QrRepository
) : ViewModel() {
    lateinit var camera: Camera

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val cameraPreviewUseCase = Preview.Builder().build()
        .apply {
            setSurfaceProvider { newSurfaceRequest ->
                _surfaceRequest.update { newSurfaceRequest }
            }
        }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                Log.d("QrScannerViewModel", "ImageAnalysis created")
                setAnalyzer(
                    ContextCompat.getMainExecutor(appContext),
                    QrCodeAnalyzer { result ->
                        Log.d("QrScannerViewModel", "QR Code scanned: $result")
                        importProtoToRoom(result)
                    }
                )
            }
        camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner,
            DEFAULT_BACK_CAMERA,
            cameraPreviewUseCase,
            imageAnalysis
        )

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }

    fun toggleFlash(on: Boolean) {
        if (::camera.isInitialized) {
            camera.cameraControl.enableTorch(on)
        }
    }

    private fun importProtoToRoom(qrData: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val id = infoRepository.importProtoToRoom(qrData)
                if (id != 0L) _uiState.update {
                    it.copy(venueId = id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onBitmapScanned(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                 bitmap.recycle()

                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val reader = MultiFormatReader().apply {
                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                }

                val result = reader.decode(binaryBitmap)
                val data = qrRepository.readTextSafely(result.text)
                importProtoToRoom(data)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Không tìm thấy QR trong vùng chọn") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

}