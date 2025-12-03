package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Node
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Upsert
    suspend fun upsert(node: Node): Long

    @Upsert
    suspend fun upsert(nodes: List<Node>)

    @Delete
    suspend fun delete(node: Node)

    @Query("SELECT * from nodes WHERE id = :id")
    fun getNodeById(id: Long): Flow<Node>

    @Query("SELECT * FROM nodes WHERE floor_id = :floorId ORDER BY id ASC")
    fun getNodesByFloorId(floorId :Long): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE floor_id = :floorId AND type != 'Intersection' AND type != 'Connector' AND label LIKE '%' || :label || '%'")
    fun getNodesByLabel(label: String, floorId :Long): Flow<List<Node>>
}