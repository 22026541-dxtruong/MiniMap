package ie.app.minimap.ui.screens.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Event
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.entity.toProto
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.VenueRepository
import ie.app.minimap.data.proto.SharedDataProto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

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

    fun generateQrCode(text: String): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = matrix.width
        val height = matrix.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun shareImage(context: Context, bitmap: Bitmap) {
//        val stream = ByteArrayOutputStream()
//        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
//        val byteArray = stream.toByteArray()
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "title", null)
        val uri: Uri = Uri.parse(path)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)  // Đính kèm ảnh
            type = "image/*"
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, null)
        context.startActivity(chooserIntent)
    }

}