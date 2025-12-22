package ie.app.minimap.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val venueRepository: VenueRepository
) : ViewModel() {

    // Trạng thái cho Danh sách Địa điểm (Venue)
    private val _venues = MutableStateFlow<List<Venue>>(emptyList())
    val venues: StateFlow<List<Venue>> = _venues.asStateFlow()

    init {
        // Tải dữ liệu khi ViewModel được khởi tạo
        loadAllVenues()
    }

    private fun loadAllVenues() {
        viewModelScope.launch {
            // Sử dụng hàm getAllVenues từ Repository
            venueRepository.getAllVenues()
                .collect { venueList ->
                    _venues.value = venueList // Cập nhật StateFlow
                }
        }
    }
}