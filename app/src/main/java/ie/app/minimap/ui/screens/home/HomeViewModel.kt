package ie.app.minimap.ui.screens.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import java.io.File
import java.io.FileOutputStream

data class HomeUiState(
    val venues: Map<String, Venue> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val venueRepository: VenueRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {
    private var _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAllVenues()
    }

    private fun loadAllVenues() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                venueRepository.getAllVenues().collect { venues ->
                    val venuesMap = mutableMapOf<String, Venue>()
                    venues.forEach { venue ->
                        venuesMap[protoToQrData(venue.id)] = venue
                    }
                    _uiState.update {
                        it.copy(
                            venues = venuesMap,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message
                    )
                }
                Log.e("HomeViewModel", "Error loading venues", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteVenue(venue: Venue) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                venueRepository.delete(venue)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message
                    )
                }
            }
        }
    }

    fun updateVenue(venue: Venue) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                venueRepository.upsert(venue)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message
                    )
                }
            }
        }
    }

    suspend fun createVenue(
        name: String,
        address: String,
        description: String
    ): Venue? {
        return try {
            _uiState.update { it.copy(isLoading = true) }
            val newVenue = venueRepository.create(Venue(name = name, address = address, description = description))
            _uiState.update { it.copy(isLoading = false) }
            newVenue
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message
                )
            }
            null
        }
    }

    suspend fun protoToQrData(venueId: Long): String {
        val proto = infoRepository.exportRoomToProto(venueId)
        val bytes = proto.toByteArray() // Protobuf binary
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun generateQrCode(context: Context, text: String, size: Int = 512): Bitmap {
        // 1. QR với Error Correction Level H
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val qrBitmap = createBitmap(size, size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                qrBitmap[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        // 2. Lấy logo từ drawable
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
            ?: return qrBitmap

        // 3. Resize logo (~20% QR)
        val scale = size * 0.2f / logoBitmap.width
        val matrixLogo = Matrix().apply { postScale(scale, scale) }
        val resizedLogo = Bitmap.createBitmap(
            logoBitmap,
            0, 0,
            logoBitmap.width,
            logoBitmap.height,
            matrixLogo,
            false
        )

        // 4. Bo tròn logo
        val roundedLogo = createBitmap(resizedLogo.width, resizedLogo.height)
        val canvas = Canvas(roundedLogo)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, resizedLogo.width, resizedLogo.height)
        val rectF = RectF(rect)
        val radius = resizedLogo.width * 0.2f // bán kính bo tròn 20% chiều rộng
        canvas.drawRoundRect(rectF, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(resizedLogo, 0f, 0f, paint)

        // 5. Chèn logo bo tròn vào trung tâm QR
        val combined = createBitmap(size, size, qrBitmap.config!!)
        val finalCanvas = Canvas(combined)
        finalCanvas.drawBitmap(qrBitmap, 0f, 0f, null)

        val left = (size - roundedLogo.width) / 2
        val top = (size - roundedLogo.height) / 2
        finalCanvas.drawBitmap(roundedLogo, left.toFloat(), top.toFloat(), null)

        return combined
    }

    fun shareImage(context: Context, bitmap: Bitmap) {
        // 1. Lưu bitmap tạm vào cache
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "shared_image.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // 2. Lấy URI qua FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider", // phải khai báo trong manifest
            file
        )

        // 3. Tạo intent share
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
    }

}