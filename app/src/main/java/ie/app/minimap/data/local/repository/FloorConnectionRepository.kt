package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.entity.FloorConnection
import kotlinx.coroutines.flow.Flow

interface FloorConnectionRepository {
    suspend fun insertFloorConnection(connection: FloorConnection)

    suspend fun updateFloorConnection(connection: FloorConnection)

    suspend fun deleteFloorConnection(connection: FloorConnection)

    fun getFloorConnectionById(id: Int): Flow<FloorConnection>

    fun getConnectionsByBuilding(buildingId: Int): Flow<List<FloorConnection>>

    fun getConnectionsByFloor(floorId: Int): Flow<List<FloorConnection>>
}