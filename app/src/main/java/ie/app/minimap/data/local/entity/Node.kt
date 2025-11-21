package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "nodes",
    foreignKeys = [
        ForeignKey(
            entity = Floor::class,
            parentColumns = ["id"],
            childColumns = ["floor_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Node(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "floor_id")
    val floorId: Long = 0,
    val label: String = "",
    val type: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    @ColumnInfo(name = "cloud_anchor_id")
    val cloudAnchorId: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROOM = "Room"
        const val BOOTH = "Booth"
        const val CONNECTOR = "Connector"
        const val INTERSECTION = "Intersection"
    }
}
