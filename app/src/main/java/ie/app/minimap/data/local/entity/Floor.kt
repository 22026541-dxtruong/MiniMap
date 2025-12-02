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
    @ColumnInfo(name = "building_id")
    val buildingId: Long = 0,
    val level: Int = 1,
    val name: String = "Floor 1",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

fun Floor.toProto(): FloorProto {
    return FloorProto.newBuilder()
        .setId(id)
        .setBuildingId(buildingId)
        .setLevel(level)
        .setName(name)
        .setCreatedAt(createdAt)
        .build()
}

fun Floor.fromProto(proto: FloorProto): Floor {
    return Floor(
        id = proto.id,
        buildingId = proto.buildingId,
        level = proto.level,
        name = proto.name,
        createdAt = proto.createdAt
    )
}
