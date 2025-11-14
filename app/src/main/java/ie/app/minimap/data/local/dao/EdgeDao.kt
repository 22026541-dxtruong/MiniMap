package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ie.app.minimap.data.local.entity.Edge
import kotlinx.coroutines.flow.Flow

@Dao
interface EdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edge: Edge)

    @Update
    suspend fun update(edge: Edge)

    @Delete
    suspend fun delete(edge: Edge)

    @Query("SELECT * FROM edges WHERE id = :id")
    fun getItem(id: Long): Flow<Edge>

    @Query("SELECT * FROM edges WHERE floor_id = :floorId ORDER BY id ASC")
    fun getEdgesByFloor(floorId: Long): Flow<List<Edge>>

}