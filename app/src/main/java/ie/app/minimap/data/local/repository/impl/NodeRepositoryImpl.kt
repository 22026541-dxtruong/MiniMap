package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.repository.NodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepositoryImpl @Inject constructor(
    private val nodeDao: NodeDao
) : NodeRepository {

    override fun getNodeById(id: Int): Flow<Node> {
        return nodeDao.getItem(id)
    }

    override fun getNodeByFloor(floorId: Int): Flow<List<Node>> {
        return nodeDao.getNodeByFloor(floorId)
    }

    override suspend fun insertNode(node: Node) {
        nodeDao.insert(node)
    }

    override suspend fun updateNode(node: Node) {
        nodeDao.update(node)
    }

    override suspend fun deleteNode(node: Node){
       nodeDao.delete(node)
    }
}