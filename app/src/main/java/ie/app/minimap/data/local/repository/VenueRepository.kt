package ie.app.minimap.data.local.repository

import androidx.room.withTransaction
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VenueRepository @Inject constructor(
    private val venueDao: VenueDao,
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val database: AppDatabase
) {

    suspend fun getVenueById(id : Long): Venue {
         return venueDao.getVenueById(id)
    }

    fun getAllVenues(): Flow<List<Venue>> {
        return venueDao.getAllVenues()
    }

    suspend fun delete(venue: Venue) {
        venueDao.delete(venue)
    }

    suspend fun upsert(venue: Venue) {
        venueDao.upsert(venue)
    }

    suspend fun create(venue: Venue): Venue {
        val venueId = database.withTransaction {
            val id = venueDao.upsert(venue)
            val buildingId = buildingDao.upsert(Building(venueId = id))
            floorDao.upsert(Floor(venueId = id, buildingId = buildingId))
            return@withTransaction id
        }
        return venue.copy(id = venueId)
    }
}