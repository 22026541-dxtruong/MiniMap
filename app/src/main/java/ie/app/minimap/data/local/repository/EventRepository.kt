package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val boothDao: BoothDao,
    private val buildingDao: BuildingDao
) {
    fun getEventByVenueId(venueId: Long): Flow<List<EventWithBuildingAndFloor>> {
        return eventDao.getEventWithBuildingAndFloor(venueId)
    }

    fun getBuildingsByVenueId(venueId: Long): Flow<List<Building>> {
        return buildingDao.getBuildingsByVenueId(venueId)

    }

//    fun getEventDetail(eventId: Long): EventWithVendors? {
//        return eventDao.getEventWithVendors(eventId)
//    }
}