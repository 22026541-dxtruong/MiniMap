package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.NodeProto

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

fun Node.toProto(): NodeProto {
    return NodeProto.newBuilder()
        .setId(id)
        .setFloorId(floorId)
        .setLabel(label)
        .setType(type)
        .setX(x)
        .setY(y)
        .setCloudAnchorId(cloudAnchorId)
        .setCreatedAt(createdAt)
        .build()
}

fun Node.fromProto(proto: NodeProto): Node {
    return Node(
        id = proto.id,
        floorId = proto.floorId,
        label = proto.label,
        type = proto.type,
        x = proto.x,
        y = proto.y,
        cloudAnchorId = proto.cloudAnchorId,
        createdAt = proto.createdAt
    )
}
