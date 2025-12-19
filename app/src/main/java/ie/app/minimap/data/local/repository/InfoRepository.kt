package ie.app.minimap.data.local.repository

import androidx.room.Transaction
import androidx.room.withTransaction
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.VendorDao
import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Event
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.local.entity.toProto
import ie.app.minimap.data.proto.SharedDataProto
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class InfoRepository @Inject constructor(
    private val db: AppDatabase,
    private val venueDao: VenueDao,
    private val vendorDao: VendorDao,
    private val boothDao: BoothDao,
    private val nodeDao: NodeDao,
    private val buildingDao: BuildingDao,
    private val floorDao: FloorDao,
    private val floorConnectionDao: FloorConnectionDao,
    private val edgeDao: EdgeDao,
    private val eventDao: EventDao
) {

    fun getShapesByLabel(label: String, floorId: Long) = nodeDao.getShapesByLabel(label, floorId)

    suspend fun getBoothWithVendorByNodeId(nodeId: Long) = boothDao.getBoothWithVendorByNodeId(nodeId)

    @Transaction
    suspend fun updateBoothAndVendor(booth: Booth, vendor: Vendor) {
        val vendorId = vendorDao.upsert(vendor)
        boothDao.upsert(booth.copy(vendorId = if (vendorId == -1L) vendor.id else vendorId))
    }

    fun getAllVendors() = vendorDao.getAllVendors()

    suspend fun upsertVendor(vendor: Vendor): Long {
        return vendorDao.upsert(vendor)
    }

    suspend fun deleteVendor(vendor: Vendor) {
        vendorDao.delete(vendor)
    }

    suspend fun upsertBooth(booth: Booth): Long {
        return boothDao.upsert(booth)
    }

    suspend fun deleteBooth(booth: Booth) {
        boothDao.delete(booth)
    }

    suspend fun importProtoToRoom(proto: SharedDataProto): Long {
        return db.withTransaction {
            val id = venueDao.upsert(Venue(proto.venue))
            vendorDao.upsert(proto.vendorList.map { Vendor(it) })
            buildingDao.upsert(proto.buildingList.map { Building(it) })
            floorDao.upsert(proto.floorList.map { Floor(it) })
            nodeDao.upsert(proto.nodeList.map { Node(it) })
            floorConnectionDao.upsert(proto.floorConnectionList.map { FloorConnection(it) })
            edgeDao.upsert(proto.edgeList.map { Edge(it) })
            boothDao.upsert(proto.boothList.map { Booth(it) })
            eventDao.upsert(proto.eventList.map { Event(it) })
            return@withTransaction id
        }
    }

    suspend fun exportRoomToProto(venueId: Long): SharedDataProto {
        return db.withTransaction {
            val venue = venueDao.getVenueById(venueId)
            val vendors = vendorDao.getVendorsByVenueId(venueId)
            val booths = boothDao.getBoothsByVenueId(venueId)
            val nodes = nodeDao.getNodesByVenueId(venueId)
            val buildings = buildingDao.getBuildingsByVenueId(venueId).first()
            val floors = floorDao.getFloorsByVenueId(venueId)
            val floorConnections = floorConnectionDao.getFloorConnectionsByVenueId(venueId)
            val edges = edgeDao.getEdgesByVenueId(venueId)
            val events = eventDao.getEventsByVenueId(venueId)
            return@withTransaction SharedDataProto.newBuilder()
                .setVenue(venue.toProto())
                .addAllNode(nodes.map { it.toProto() })
                .addAllVendor(vendors.map { it.toProto() })
                .addAllBuilding(buildings.map { it.toProto() })
                .addAllEdge(edges.map { it.toProto() })
                .addAllFloorConnection(floorConnections.map { it.toProto() })
                .addAllBooth(booths.map { it.toProto() })
                .addAllFloor(floors.map { it.toProto() })
                .addAllEvent(events.map { it.toProto() })
                .build()
        }
    }

}