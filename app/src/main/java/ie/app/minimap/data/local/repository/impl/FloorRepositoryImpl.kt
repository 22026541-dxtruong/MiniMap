package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.repository.FloorRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloorRepositoryImpl @Inject constructor(
    private val floorDao: FloorDao
): FloorRepository {
    override fun getFloorById(id: Int): Flow<Floor> {
        return floorDao.getItem(id)
    }

    override fun getFloorsByBuilding(buildingId: Int): Flow<List<Floor>> {
        return floorDao.getFloorsByBuilding(buildingId)
    }

    override suspend fun deleteFloor(floor: Floor) {
        return floorDao.delete(floor)
    }

    override suspend fun insertFloor(floor: Floor) {
        return floorDao.insert(floor)
    }

    override suspend fun updateFloor(floor: Floor) {
        return floorDao.insert(floor)
    }
}