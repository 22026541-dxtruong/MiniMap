package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "shapes",
    foreignKeys = [
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Shape(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "node_id")
    val nodeId: Long,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    val label: String = "",
    val shape: ShapeType,
    val color: Long,
    val width: Float,
    val height: Float,
    @ColumnInfo(name = "center_x")
    val centerX: Float,
    @ColumnInfo(name = "center_y")
    val centerY: Float,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        enum class ShapeType {
            RECTANGLE, CIRCLE, TRIANGLE, DIAMOND, PENTAGON, HEXAGON
        }
    }
}
