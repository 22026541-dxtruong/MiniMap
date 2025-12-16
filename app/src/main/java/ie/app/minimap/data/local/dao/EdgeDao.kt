package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Edge
import kotlinx.coroutines.flow.Flow

@Dao
interface EdgeDao {

    @Upsert
    suspend fun upsert(edge: Edge): Long

    @Upsert
    suspend fun upsert(edges: List<Edge>)

    @Delete
    suspend fun delete(edge: Edge)

    @Query("DELETE FROM edges WHERE from_node = :nodeId OR to_node = :nodeId")
    suspend fun deleteEdgesByNodeId(nodeId: Long)

    @Query("SELECT * FROM edges WHERE id = :id")
    fun getEdgeById(id: Long): Flow<Edge>

    @Query("SELECT * FROM edges WHERE floor_id = :floorId ORDER BY id ASC")
    fun getEdgesByFloorId(floorId: Long): Flow<List<Edge>>

    @Query("SELECT * FROM edges WHERE venue_id = :venueId ORDER BY id ASC")
    fun getEdgesByVenueId(venueId: Long): List<Edge>

}