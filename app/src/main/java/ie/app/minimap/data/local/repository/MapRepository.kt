package ie.app.minimap.data.local.repository

import androidx.room.withTransaction
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.ShapeDao
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape
import ie.app.minimap.data.local.relations.FloorWithNodesAndEdges
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MapRepository @Inject constructor(
    private val database: AppDatabase,
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val floorConnectionDao: FloorConnectionDao,
    private val shapeDao: ShapeDao
) {

    fun getBuildingById(id: Long): Flow<Building> {
        return buildingDao.getBuildingById(id)
    }

    fun getFloorsByBuildingId(buildingId: Long): Flow<List<Floor>> {
        return floorDao.getFloorsByBuildingId(buildingId)
    }

    fun getFloorWithNodesAndEdgeByFloorId(id: Long): Flow<FloorWithNodesAndEdges?> {
        return floorDao.getFloorWithNodesAndEdgesById(id)
    }

    fun getAllBuildingsByVenueId(venueId: Long): Flow<List<Building>> {
        return buildingDao.getBuildingsByVenueId(venueId)
    }

    suspend fun upsertFloor(floor: Floor): Long {
        return floorDao.upsert(floor)
    }

    suspend fun deleteFloor(floor: Floor) {
        floorDao.delete(floor)
    }

    suspend fun upsertNode(node: Node): Long {
        return nodeDao.upsert(node)
    }

    suspend fun deleteNode(node: Node) {
        nodeDao.delete(node)
    }

    suspend fun upsertShape(shape: Shape): Long {
        return shapeDao.upsert(shape)
    }

    suspend fun deleteShape(shape: Shape) {
        shapeDao.delete(shape)
    }

    suspend fun deleteEdgesByNodeId(nodeId: Long) {
        edgeDao.deleteEdgesByNodeId(nodeId)
    }

    suspend fun upsertEdge(edge: Edge): Long {
        return edgeDao.upsert(edge)
    }

    suspend fun deleteEdge(edge: Edge) {
        edgeDao.delete(edge)
    }

    suspend fun upsertBuilding(building: Building): Long {
        return database.withTransaction {
            val isNew = building.id == 0L
            val resultId = buildingDao.upsert(building)
            // Nếu là cập nhật, resultId sẽ là -1, ta lấy id cũ từ object building
            val finalBuildingId = if (isNew) resultId else building.id
            
            if (isNew) {
                // Chỉ tạo tầng mặc định nếu là tòa nhà mới
                floorDao.upsert(Floor(venueId = building.venueId, buildingId = finalBuildingId))
            }
            return@withTransaction finalBuildingId
        }
    }

    suspend fun deleteBuilding(building: Building) {
        buildingDao.delete(building)
    }

    suspend fun upsertFloorConnection(floorConnection: FloorConnection): Long {
        return floorConnectionDao.upsert(floorConnection)
    }

    suspend fun deleteFloorConnection(floorConnection: FloorConnection) {
        floorConnectionDao.delete(floorConnection)
    }
}