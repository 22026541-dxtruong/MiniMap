package ie.app.minimap.data.local.repository.impl

import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dto.EventBuildingFloorDto
import ie.app.minimap.data.local.relations.EventWithVendors
import ie.app.minimap.data.local.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao
) : EventRepository{
    override suspend fun getEventByVenueId(venueId: Long): Flow<List<EventBuildingFloorDto>> {
        return eventDao.getEventBuildingFloor(venueId)
    }

    override suspend fun getEventDetail(eventId: Long): EventWithVendors? {
        return eventDao.getEventWithVendors(eventId)
    }
}