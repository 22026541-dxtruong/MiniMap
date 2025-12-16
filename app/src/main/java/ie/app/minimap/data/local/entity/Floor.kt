package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.FloorProto

@Entity(
    tableName = "floors",
    foreignKeys = [
        ForeignKey(
            entity = Building::class,
            parentColumns = ["id"],
            childColumns = ["building_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Floor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    @ColumnInfo(name = "building_id")
    val buildingId: Long = 0,
    val level: Int = 1,
    val name: String = "Floor 1",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor(proto: FloorProto) : this(
        id = proto.id,
        venueId = proto.venueId,
        buildingId = proto.buildingId,
        level = proto.level,
        name = proto.name,
        createdAt = proto.createdAt
    )
}

fun Floor.toProto(): FloorProto {
    return FloorProto.newBuilder()
        .setId(id)
        .setVenueId(venueId)
        .setBuildingId(buildingId)
        .setLevel(level)
        .setName(name)
        .setCreatedAt(createdAt)
        .build()
}
