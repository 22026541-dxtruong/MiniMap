package ie.app.minimap.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node

data class FloorWithNodesAndEdges(
    @Embedded
    val floor: Floor,
    @Relation(
        entity = Node::class,
        parentColumn = "id",
        entityColumn = "floor_id"
    )
    val nodeWithShapes: List<NodeWithShape> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "floor_id"
    )
    val edges: List<Edge> = emptyList()
)