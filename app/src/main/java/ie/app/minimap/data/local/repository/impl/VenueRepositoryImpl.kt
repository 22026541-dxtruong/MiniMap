package ie.app.minimap.data.local.repository.impl

import androidx.room.withTransaction
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.FloorDao
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
    private val venueDao: VenueDao,
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val database: AppDatabase
) : VenueRepository {

    override fun getVenuesById(id : Long): Flow<Venue> {
         return venueDao.getVenueById(id)
    }

    override fun getAllVenues(): Flow<List<Venue>> {
        return venueDao.getAllVenues()
    }

    override suspend fun delete(venue: Venue) {
        venueDao.delete(venue)
    }

    override suspend fun create(venue: Venue): Venue {
        val venueId = database.withTransaction {
            val id = venueDao.upsert(venue)
            val buildingId = buildingDao.upsert(Building(venueId = id))
            floorDao.upsert(Floor(buildingId = buildingId))
            return@withTransaction id
        }
        return venue.copy(id = venueId)
    }
}