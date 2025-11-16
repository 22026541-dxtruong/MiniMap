package ie.app.minimap.ui.screens.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.dto.EventBuildingFloorDto
import ie.app.minimap.data.local.relations.EventWithVendors
import ie.app.minimap.data.local.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VenueViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _events = MutableStateFlow<List<EventBuildingFloorDto>>(emptyList())
    val events: StateFlow<List<EventBuildingFloorDto>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Load Events + Buildings + Floors for a venue */
    fun fetchEvents(venueId: Long) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            eventRepository.getEventByVenueId(venueId)
                .catch { e ->
                    _error.value = e.message
                    _loading.value = false
                }
                .collectLatest { list ->
                    _events.value = list
                    _loading.value = false
                }
        }
    }

    // -----------------------------
    // Event Detail with Vendors + Booths
    // -----------------------------

    private val _selectedEvent = MutableStateFlow<EventWithVendors?>(null)
    val selectedEvent: StateFlow<EventWithVendors?> = _selectedEvent.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    /** Load Event Detail by eventId (Event → Vendors → Booths) */
    fun fetchEventDetail(eventId: Long) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null

            try {
                val eventDetail = eventRepository.getEventDetail(eventId)
                _selectedEvent.value = eventDetail
            } catch (e: Exception) {
                _detailError.value = e.message
            } finally {
                _detailLoading.value = false
            }
        }

}
}