package ie.app.minimap.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.VenueRepository
import ie.app.minimap.di.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import java.io.File

data class HomeUiState(
    val venues: List<Venue> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val venueRepository: VenueRepository,
    private val infoRepository: InfoRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    private var _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAllVenues()
        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
    }

    private fun loadAllVenues() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                venueRepository.getAllVenues().collect { venues ->
                    _uiState.update {
                        it.copy(
                            venues = venues,
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

    suspend fun importData(context: Context, uri: Uri): Long? {
        _uiState.update { it.copy(isLoading = true) }
        return try {
            val text = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                .use { it?.readText() }

            text?.let {
                infoRepository.importProtoToRoom(it)
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error importing data", e)
            _uiState.update { it.copy(error = e.message) }
            null
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun protoToQrData(venueId: Long): String {
        val proto = infoRepository.exportRoomToProto(venueId)
        val bytes = proto.toByteArray() // Protobuf binary
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun shareFile(context: Context, venueId: Long, fileName: String) {
        val content = protoToQrData(venueId)

        val file = File(context.cacheDir, "${fileName}.txt")
        file.writeText(content)

        // 2. Lấy URI qua FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share File"))
    }

}
