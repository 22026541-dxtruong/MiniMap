package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.relations.FloorWithNodesAndEdges
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorDao {

    @Upsert
    suspend fun upsert(floor: Floor): Long

    @Upsert
    suspend fun upsert(floors: List<Floor>)

    @Delete
    suspend fun delete(floor: Floor)

    @Transaction
    @Query("SELECT * FROM floors WHERE id = :id")
    fun getFloorWithNodesAndEdgesById(id: Long): Flow<FloorWithNodesAndEdges>

    @Query("SELECT * FROM floors WHERE building_id = :buildingId ORDER BY level ASC")
    fun getFloorsByBuildingId(buildingId: Long): Flow<List<Floor>>

    @Query("SELECT * FROM floors WHERE venue_id = :venueId ORDER BY level ASC")
    fun getFloorsByVenueId(venueId: Long): Flow<List<Floor>>

}