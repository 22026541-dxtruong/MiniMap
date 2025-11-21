package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.relations.BuildingWithFloors
import ie.app.minimap.data.local.relations.FloorWithNodesAndEdges
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MapRepository @Inject constructor(
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val floorConnectionDao: FloorConnectionDao
) {

    fun getBuildingWithFloorsByBuildingId(id: Long): Flow<BuildingWithFloors> {
        return buildingDao.getBuildingWithFloorsById(id)
    }

    fun getFloorWithNodesAndEdgeByFloorId(id: Long): Flow<FloorWithNodesAndEdges> {
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
        return buildingDao.upsert(building)
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