package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.repository.VenueRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VenueRepositoryImpl @Inject constructor(
    private val venueDao: VenueDao
) : VenueRepository {
    override fun getVenuesById(id : Int): Flow<List<Venue>> {
         return venueDao.getVenuebyId(id)
    }

    override suspend fun createVenueWithDefaults(
        name: String,
        address: String,
        description: String
    ): Triple<Venue, Building, Floor> {
        return venueDao.createVenueWithDefaults(name, address, description)
    }
}