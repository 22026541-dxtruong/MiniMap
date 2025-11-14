package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ie.app.minimap.data.local.entity.Node
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(node: Node)

    @Update
    suspend fun update(node: Node)

    @Delete
    suspend fun delete(node: Node)

    @Query("SELECT * from nodes WHERE id = :id")
    fun getItem(id: Int): Flow<Node>

    @Query("SELECT * FROM nodes WHERE floor_id = :floorId ORDER BY id ASC")
    fun getNodeByFloor(floorId :Int): Flow<List<Node>>
}