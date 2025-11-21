package ie.app.minimap.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val venues: List<Venue> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val venueRepository: VenueRepository
) : ViewModel() {
    private var _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAllVenues()
    }

    private fun loadAllVenues() {
        viewModelScope.launch {
            try {
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
                        isLoading = false,
                        error = e.message
                    )
                }
                Log.e("HomeViewModel", "Error loading venues", e)

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

}