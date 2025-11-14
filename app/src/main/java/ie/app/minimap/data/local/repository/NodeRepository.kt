package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.entity.Node
import kotlinx.coroutines.flow.Flow

interface NodeRepository {
    fun getNodeById (id: Int) : Flow<Node>

    fun getNodeByFloor(floorId :Int): Flow<List<Node>>

    suspend fun insertNode(node: Node)

    suspend fun  updateNode(node: Node)

    suspend fun deleteNode(node: Node)
}