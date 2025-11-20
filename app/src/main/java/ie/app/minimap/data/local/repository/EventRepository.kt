package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dto.EventBuildingFloorDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val eventDao: EventDao
) {
    fun getEventByVenueId(venueId: Long): Flow<List<EventBuildingFloorDto>> {
        return eventDao.getEventBuildingFloor(venueId)
    }

//    fun getEventDetail(eventId: Long): EventWithVendors? {
//        return eventDao.getEventWithVendors(eventId)
//    }
}