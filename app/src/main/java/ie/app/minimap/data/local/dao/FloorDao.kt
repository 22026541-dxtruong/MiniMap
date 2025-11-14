package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ie.app.minimap.data.local.entity.Floor
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(floor: Floor)

    @Update
    suspend fun update(floor: Floor)

    @Delete
    suspend fun delete(floor: Floor)

    @Query("SELECT * FROM floors WHERE id = :id")
    fun getItem(id: Int): Flow<Floor>

    @Query("SELECT * FROM floors WHERE building_id = :buildingId ORDER BY level ASC")
    fun getFloorsByBuilding(buildingId: Int): Flow<List<Floor>>

}