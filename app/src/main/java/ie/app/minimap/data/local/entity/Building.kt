package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.BuildingProto

@Entity(
    tableName = "buildings",
    foreignKeys = [
        ForeignKey(
            entity = Venue::class,
            parentColumns = ["id"],
            childColumns = ["venue_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Building(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    val name: String = "Building 1",
    val description: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor(proto: BuildingProto) : this(
        id = proto.id,
        venueId = proto.venueId,
        name = proto.name,
        description = proto.description,
        createdAt = proto.createdAt
    )
}

fun Building.toProto(): BuildingProto {
    return BuildingProto.newBuilder()
        .setId(id)
        .setVenueId(venueId)
        .setName(name)
        .setDescription(description)
        .setCreatedAt(createdAt)
        .build()
}
