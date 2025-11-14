package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.repository.EdgeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeRepositoryImpl @Inject constructor(
    private val edgeDao: EdgeDao
) : EdgeRepository{
    override suspend fun delete(edge: Edge) {
       edgeDao.delete(edge)
    }

    override fun getEdgesByFloor(floorId: Long): Flow<List<Edge>> {
        return edgeDao.getEdgesByFloor(floorId)
    }

    override fun getEdgeById(id: Long): Flow<Edge> {
        return edgeDao.getItem(id)
    }

    override suspend fun insert(edge: Edge) {
        edgeDao.insert(edge)
    }

    override suspend fun update(edge: Edge) {
        edgeDao.insert(edge)
    }
}