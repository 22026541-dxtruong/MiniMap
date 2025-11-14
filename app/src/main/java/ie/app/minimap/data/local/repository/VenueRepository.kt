package ie.app.minimap.data.local.repository;

import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.flow.Flow

interface VenueRepository {
    fun getVenuesById(id : Long): Flow<List<Venue>>

    suspend fun createVenueWithDefaults(
        name: String,
        address: String,
        description: String
    ): Triple<Venue, Building, Floor>

}
