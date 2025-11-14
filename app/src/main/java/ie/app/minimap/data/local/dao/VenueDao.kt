package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.flow.Flow

@Dao
interface VenueDao {
    @Query("SELECT * from venues WHERE id = :id")
    fun getVenueById(id: Long): Flow<List<Venue>>

    @Insert
    suspend fun insertVenue(venue: Venue): Long

    @Insert
    suspend fun insertBuilding(building: Building): Long

    @Insert
    suspend fun insertFloor(floor: Floor): Long

    @Transaction
    suspend fun createVenueWithDefaults(
        name: String,
        address: String,
        description: String
    ): Triple<Venue, Building, Floor> {

        val venue = Venue(name = name, address = address, description = description)
        val venueId = insertVenue(venue)

        val building = Building(
            venueId = venueId,
            name = "Main Building",
            description = "Default building for this venue"
        )
        val buildingId = insertBuilding(building)

        val floor = Floor(
            buildingId = buildingId,
            level = 1,
            name = "Ground Floor"
        )
        val floorId = insertFloor(floor)

        return Triple(
            venue.copy(id = venueId),
            building.copy(id = buildingId),
            floor.copy(id = floorId)
        )
    }
}