package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.relations.BuildingWithFloors
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {

    @Upsert
    suspend fun upsert(building: Building): Long

    @Delete
    suspend fun delete(building: Building)

    @Transaction
    @Query("SELECT * FROM buildings WHERE id = :id")
    fun getBuildingWithFloorsById(id: Long): Flow<BuildingWithFloors>

    @Query("SELECT * FROM buildings WHERE venue_id = :venueId ORDER BY id ASC")
    fun getBuildingsByVenueId(venueId: Long): Flow<List<Building>>

}