package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.relations.BuildingWithFloors
import ie.app.minimap.data.local.relations.FloorWithNodesAndEdges
import kotlinx.coroutines.flow.Flow

interface MapRepository {

    fun getBuildingWithFloorsByBuildingId(id: Long): Flow<BuildingWithFloors>

    fun getFloorWithNodesAndEdgeByFloorId(id: Long): Flow<FloorWithNodesAndEdges>

    fun getAllBuildingsByVenueId(venueId: Long): Flow<List<Building>>

    suspend fun upsertFloor(floor: Floor): Long

    suspend fun deleteFloor(floor: Floor)

    suspend fun upsertNode(node: Node): Long

    suspend fun deleteNode(node: Node)

    suspend fun deleteEdgesByNodeId(nodeId: Long)

    suspend fun upsertEdge(edge: Edge): Long

    suspend fun deleteEdge(edge: Edge)

    suspend fun upsertBuilding(building: Building): Long

    suspend fun deleteBuilding(building: Building)

    suspend fun upsertFloorConnection(floorConnection: FloorConnection): Long

    suspend fun deleteFloorConnection(floorConnection: FloorConnection)

}
