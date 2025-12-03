package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val eventDao: EventDao
) {
    fun getEventByVenueId(venueId: Long): Flow<List<EventWithBuildingAndFloor>> {
        return eventDao.getEventWithBuildingAndFloor(venueId)
    }

//    fun getEventDetail(eventId: Long): EventWithVendors? {
//        return eventDao.getEventWithVendors(eventId)
//    }
}