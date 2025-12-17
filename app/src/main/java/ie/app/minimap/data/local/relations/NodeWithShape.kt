package ie.app.minimap.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape

data class NodeWithShape(
    @Embedded
    val node: Node,
    @Relation(
        parentColumn = "id",
        entityColumn = "node_id"
    )
    val shape: Shape? = null,
)