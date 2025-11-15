package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VenueRepositoryImpl @Inject constructor(
    private val venueDao: VenueDao
) : VenueRepository {

    override fun getVenuesById(id : Long): Flow<Venue> {
         return venueDao.getVenueById(id)
    }

    override fun getAllVenues(): Flow<List<Venue>> {
        return venueDao.getAllVenues()
    }

    override suspend fun deleteVenue(venue: Venue) {
        return venueDao.deleteVenue(venue)
    }

    override suspend fun createVenueWithDefaults(
        name: String,
        address: String,
        description: String
    ): Venue{
        return venueDao.createVenueWithDefaults(name, address, description)
    }
}