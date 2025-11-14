package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ie.app.minimap.data.local.entity.FloorConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: FloorConnection)

    @Update
    suspend fun update(connection: FloorConnection)

    @Delete
    suspend fun delete(connection: FloorConnection)

    @Query("SELECT * FROM floor_connections WHERE id = :id")
    fun getItem(id: Long): Flow<FloorConnection>

    @Query("""
        SELECT * FROM floor_connections 
        WHERE building_id = :buildingId 
        ORDER BY from_floor, type ASC
    """)
    fun getConnectionsByBuilding(buildingId: Long): Flow<List<FloorConnection>>

    @Query("""
        SELECT * FROM floor_connections 
        WHERE from_floor = :floorId OR to_floor = :floorId
        ORDER BY id ASC
    """)
    fun getConnectionsByFloor(floorId: Long): Flow<List<FloorConnection>>

}