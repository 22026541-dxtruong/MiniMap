package ie.app.minimap.ui.screens.event

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.EventRepository
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import ie.app.minimap.data.local.entity.Event
import kotlinx.coroutines.flow.update

data class EventUiState(
    val events: List<EventWithBuildingAndFloor> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EventViewModel @Inject constructor(
    private val venueRepository: VenueRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _venue = MutableStateFlow<Venue?>(null)
    val venue: StateFlow<Venue?> = _venue.asStateFlow()

    private val _uiState = MutableStateFlow(EventUiState(isLoading = true))
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _vendors = MutableStateFlow<List<Vendor>>(emptyList())
    val vendors: StateFlow<List<Vendor>> = _vendors.asStateFlow()

    private val _booths = MutableStateFlow<List<Booth>>(emptyList())
    val booths: StateFlow<List<Booth>> = _booths

    /**
     * Tải thông tin chi tiết của Venue từ Repository
     */
    fun loadEvents(venueId: Long) {
        viewModelScope.launch {
            try {
                eventRepository.getEventByVenueId(venueId = venueId).collect {
                    _uiState.value = EventUiState(events = it)
                }
            } catch (e: Exception) {
                _uiState.value = EventUiState(error = e.message)
            }
        }
    }

    fun loadVenueDetails(venueId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = venueRepository.getVenueById(venueId)
                _venue.value = result
            } catch (e: Exception) {

            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    fun createEvent(event: Event) {
        viewModelScope.launch {
            try {
                eventRepository.upsert(event)
                loadEvents(event.venueId)
            } catch (e: Exception) {
                Log.e("DB_ERROR", "Không thể tạo event: ${e.message}")
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            try {
                eventRepository.deleteEvent(event)
                loadEvents(event.venueId)
                Log.d("ViewModel", "Xóa thành công event: ${event.name}")
            } catch (e: Exception) {
                Log.e("ViewModel", "Lỗi khi xóa event", e)
            }
        }
    }

    fun loadBooths(venueId: Long) {
        viewModelScope.launch {
            _booths.value = eventRepository.getBoothsByVenueId(venueId)
        }
    }

    fun loadVendors(venueId: Long) {
        viewModelScope.launch {
            try {
                val result = eventRepository.getVendorsByVenueId(venueId)
                _vendors.value = result
            } catch (e: Exception) {
            }
        }
    }
}