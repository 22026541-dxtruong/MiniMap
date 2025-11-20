package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "booths",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendor_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Venue::class,
            parentColumns = ["id"],
            childColumns = ["venue_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Building::class,
            parentColumns = ["id"],
            childColumns = ["building_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Floor::class,
            parentColumns = ["id"],
            childColumns = ["floor_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Booth(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "vendor_id")
    val vendorId: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    @ColumnInfo(name = "building_id")
    val buildingId: Long = 0,
    @ColumnInfo(name = "floor_id")
    val floorId: Long = 0,
    @ColumnInfo(name = "node_id")
    val nodeId: Long = 0,
    val name: String = "",
    val description: String = "",
    val category: String = "",
    @ColumnInfo(name = "img_url")
    val imgUrl: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
