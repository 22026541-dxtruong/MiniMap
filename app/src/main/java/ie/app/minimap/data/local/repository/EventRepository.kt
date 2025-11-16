package ie.app.minimap.data.local.repository

import ie.app.minimap.data.local.dto.EventBuildingFloorDto
import ie.app.minimap.data.local.relations.EventWithVendors
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    suspend fun getEventByVenueId(venueId : Long) : Flow<List<EventBuildingFloorDto>>

    suspend fun getEventDetail(eventId: Long) : EventWithVendors?
}