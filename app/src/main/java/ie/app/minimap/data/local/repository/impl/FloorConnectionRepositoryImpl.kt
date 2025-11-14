package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.repository.FloorConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloorConnectionRepositoryImpl @Inject constructor(
    private val floorConnectionDao: FloorConnectionDao
) : FloorConnectionRepository {
    override fun getConnectionsByBuilding(buildingId: Long): Flow<List<FloorConnection>> {
        return floorConnectionDao.getConnectionsByBuilding(buildingId)
    }

    override suspend fun deleteFloorConnection(connection: FloorConnection) {
        floorConnectionDao.delete(connection)
    }

    override fun getConnectionsByFloor(floorId: Long): Flow<List<FloorConnection>> {
        return floorConnectionDao.getConnectionsByFloor(floorId)
    }

    override fun getFloorConnectionById(id: Long): Flow<FloorConnection> {
        return floorConnectionDao.getItem(id)
    }

    override suspend fun insertFloorConnection(connection: FloorConnection) {
        floorConnectionDao.insert(connection)
    }

    override suspend fun updateFloorConnection(connection: FloorConnection) {
        floorConnectionDao.update(connection)
    }
}