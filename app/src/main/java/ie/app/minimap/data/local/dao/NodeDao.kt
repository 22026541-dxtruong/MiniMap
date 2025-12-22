package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.relations.NodeWithShape
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

    @Query("SELECT * FROM nodes WHERE venue_id = :venueId ORDER BY id ASC")
    fun getNodesByVenueId(venueId: Long): List<Node>

    @Transaction
    @Query("SELECT * FROM nodes " +
            "JOIN shapes ON shapes.node_id = nodes.id " +
            "WHERE shapes.venue_id = :venueId AND type IN ('Room', 'Booth') AND label LIKE '%' || :label || '%'")
    fun getShapesByLabel(label: String, venueId :Long): Flow<List<NodeWithShape>>
}
