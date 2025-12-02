package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.VenueProto

@Entity(
    tableName = "venues"
)
data class Venue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Venue $id",
    val address: String = "Address",
    val description: String = "Description",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

fun Venue.toProto(): VenueProto {
    return VenueProto.newBuilder()
        .setId(id)
        .setName(name)
        .setAddress(address)
        .setDescription(description)
        .setCreatedAt(createdAt)
        .build()
}

fun Venue.fromProto(proto: VenueProto): Venue {
    return Venue(
        id = proto.id,
        name = proto.name,
        address = proto.address,
        description = proto.description,
        createdAt = proto.createdAt
    )
}
