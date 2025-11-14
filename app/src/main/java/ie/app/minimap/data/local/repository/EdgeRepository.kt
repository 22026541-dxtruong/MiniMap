package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.entity.Edge
import kotlinx.coroutines.flow.Flow

interface EdgeRepository {
    suspend fun insert(edge: Edge)

    suspend fun update(edge: Edge)

    suspend fun delete(edge: Edge)

    fun getEdgeById(id: Int): Flow<Edge>

    fun getEdgesByFloor(floorId: Int): Flow<List<Edge>>
}