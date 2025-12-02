package ie.app.minimap.ui.screens.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventUiState(
    val events: List<EventWithBuildingAndFloor> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository
): ViewModel() {
    private val _uiState = MutableStateFlow(EventUiState(isLoading = true))
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

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
}
