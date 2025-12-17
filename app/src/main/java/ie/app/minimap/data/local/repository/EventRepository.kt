package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.VendorDao
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Vendor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val boothDao: BoothDao,
    private val buildingDao: BuildingDao,
    private val vendorDao: VendorDao
) {
    fun getEventByVenueId(venueId: Long): Flow<List<EventWithBuildingAndFloor>> {
        return eventDao.getEventWithBuildingAndFloor(venueId)
    }
    fun getBuildingsByVenueId(venueId: Long): Flow<List<Building>> {
        return buildingDao.getBuildingsByVenueId(venueId)

    }
    suspend fun getVendorsByVenueId(venueId: Long):List<Vendor> {
        return vendorDao.getVendorsByVenueId(venueId)
    }

//    fun getEventDetail(eventId: Long): EventWithVendors? {
//        return eventDao.getEventWithVendors(eventId)
//    }
}