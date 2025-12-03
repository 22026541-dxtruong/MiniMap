package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.FloorConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorConnectionDao {

    @Upsert
    suspend fun upsert(connection: FloorConnection): Long

    @Upsert
    suspend fun upsert(connections: List<FloorConnection>)

    @Delete
    suspend fun delete(connection: FloorConnection)

    @Query("SELECT * FROM floor_connections WHERE id = :id")
    fun getFloorConnectionById(id: Long): Flow<FloorConnection>

    @Query("""
        SELECT * FROM floor_connections 
        WHERE building_id = :buildingId 
        ORDER BY from_floor, type ASC
    """)
    fun getFloorConnectionsByBuildingId(buildingId: Long): Flow<List<FloorConnection>>

    @Query("""
        SELECT * FROM floor_connections 
        WHERE from_floor = :floorId OR to_floor = :floorId
        ORDER BY id ASC
    """)
    fun getFloorConnectionsByFloorId(floorId: Long): Flow<List<FloorConnection>>

}