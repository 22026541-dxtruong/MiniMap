package ie.app.minimap.data.local.repository;

import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.flow.Flow

interface VenueRepository {
    fun getVenuesById(id : Long): Flow<Venue>

    fun getAllVenues(): Flow<List<Venue>>

    suspend fun deleteVenue(venue: Venue)

    suspend fun createVenueWithDefaults(
        name: String,
        address: String,
        description: String
    ): Venue

}
