package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.EdgeProto

@Entity(
    tableName = "edges",
    foreignKeys = [
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
            childColumns = ["from_node"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["to_node"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Edge(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    @ColumnInfo(name = "floor_id")
    val floorId: Long = 0,
    @ColumnInfo(name = "from_node")
    val fromNode: Long = 0,
    @ColumnInfo(name = "to_node")
    val toNode: Long = 0,
    val weight: Float = 0f,
    val type: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor(proto: EdgeProto) : this(
        id = proto.id,
        venueId = proto.venueId,
        floorId = proto.floorId,
        fromNode = proto.fromNode,
        toNode = proto.toNode,
        weight = proto.weight,
        type = proto.type,
        createdAt = proto.createdAt
    )
}

fun Edge.toProto(): EdgeProto {
    return EdgeProto.newBuilder()
        .setId(id)
        .setVenueId(venueId)
        .setFloorId(floorId)
        .setFromNode(fromNode)
        .setToNode(toNode)
        .setWeight(weight)
        .setType(type)
        .setCreatedAt(createdAt)
        .build()
}
