package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.VendorDao
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Vendor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InfoRepository @Inject constructor(
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
}