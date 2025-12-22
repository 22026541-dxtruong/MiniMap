package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape
import kotlinx.coroutines.flow.Flow

@Dao
interface ShapeDao {
    @Upsert
    suspend fun upsert(shape: Shape): Long

    @Upsert
    suspend fun upsert(shapes: List<Shape>)

    @Delete
    suspend fun delete(shape: Shape)

    @Query("SELECT * from shapes WHERE id = :id")
    fun getShapeById(id: Long): Flow<Shape>

    @Query("SELECT * FROM shapes WHERE venue_id = :venueId ORDER BY id ASC")
    fun getShapesByVenueId(venueId: Long): List<Shape>

    @Query("SELECT * FROM shapes WHERE node_id = :nodeId ORDER BY id ASC")
    fun getShapesByNodeId(nodeId :Long): Flow<Shape>
}