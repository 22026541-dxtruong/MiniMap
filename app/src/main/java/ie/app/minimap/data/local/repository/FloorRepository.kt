package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.entity.Floor
import kotlinx.coroutines.flow.Flow


interface FloorRepository {
    fun getFloorsByBuilding (buildingId: Long): Flow<List<Floor>>

    fun getFloorById (id: Long): Flow<Floor>

    suspend fun deleteFloor (floor: Floor)

    suspend fun updateFloor (floor: Floor)

    suspend fun insertFloor (floor: Floor)
}