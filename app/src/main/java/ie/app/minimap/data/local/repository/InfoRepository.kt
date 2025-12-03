package ie.app.minimap.data.local.repository

import androidx.room.withTransaction
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.VendorDao
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Event
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.entity.Venue
import ie.app.minimap.data.proto.SharedDataProto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InfoRepository @Inject constructor(
    private val db: AppDatabase,
    private val vendorDao: VendorDao,
    private val boothDao: BoothDao,
    private val nodeDao: NodeDao,
    private val eventDao: EventDao
) {

    fun getNodesByLabel(label: String, floorId :Long) = nodeDao.getNodesByLabel(label, floorId)

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

    fun getEventByVenueId(venueId: Long): Flow<List<EventWithBuildingAndFloor>> {
        return eventDao.getEventWithBuildingAndFloor(venueId)
    }

    suspend fun importProtoToRoom(proto: SharedDataProto): Long {
        return db.withTransaction {
            val id = db.venueDao().upsert(Venue(proto.venue))
            vendorDao.upsert(proto.vendorList.map { Vendor(it) })
            db.buildingDao().upsert(proto.buildingList.map { Building(it) })
            db.floorDao().upsert(proto.floorList.map { Floor(it) })
            nodeDao.upsert(proto.nodeList.map { Node(it) })
            db.floorConnectionDao().upsert(proto.floorConnectionList.map { FloorConnection(it) })
            db.edgeDao().upsert(proto.edgeList.map { Edge(it) })
            boothDao.upsert(proto.boothList.map { Booth(it) })
            eventDao.upsert(proto.eventList.map { Event(it) })
            return@withTransaction id
        }
    }
}