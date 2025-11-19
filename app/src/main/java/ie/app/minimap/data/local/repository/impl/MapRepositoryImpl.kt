package ie.app.minimap.data.local.repository.impl

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
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MapRepositoryImpl @Inject constructor(
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val floorConnectionDao: FloorConnectionDao
) : MapRepository {

    override fun getBuildingWithFloorsByBuildingId(id: Long): Flow<BuildingWithFloors> {
        return buildingDao.getBuildingWithFloorsById(id)
    }

    override fun getFloorWithNodesAndEdgeByFloorId(id: Long): Flow<FloorWithNodesAndEdges> {
        return floorDao.getFloorWithNodesAndEdgesById(id)
    }

    override fun getAllBuildingsByVenueId(venueId: Long): Flow<List<Building>> {
        return buildingDao.getBuildingsByVenueId(venueId)
    }

    override suspend fun upsertFloor(floor: Floor): Long {
        return floorDao.upsert(floor)
    }

    override suspend fun deleteFloor(floor: Floor) {
        floorDao.delete(floor)
    }

    override suspend fun upsertNode(node: Node): Long {
        return nodeDao.upsert(node)
    }

    override suspend fun deleteNode(node: Node) {
        nodeDao.delete(node)
    }

    override suspend fun deleteEdgesByNodeId(nodeId: Long) {
        edgeDao.deleteEdgesByNodeId(nodeId)
    }

    override suspend fun upsertEdge(edge: Edge): Long {
        return edgeDao.upsert(edge)
    }

    override suspend fun deleteEdge(edge: Edge) {
        edgeDao.delete(edge)
    }

    override suspend fun upsertBuilding(building: Building): Long {
        return buildingDao.upsert(building)
    }

    override suspend fun deleteBuilding(building: Building) {
        buildingDao.delete(building)
    }

    override suspend fun upsertFloorConnection(floorConnection: FloorConnection): Long {
        return floorConnectionDao.upsert(floorConnection)
    }

    override suspend fun deleteFloorConnection(floorConnection: FloorConnection) {
        floorConnectionDao.delete(floorConnection)
    }
}