package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import ie.app.minimap.data.local.dto.EventBuildingFloorDto
import ie.app.minimap.data.local.relations.EventWithVendors
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("""
        SELECT 
            e.id AS eventId,
            e.name AS eventName,
            e.description AS eventDescription,
            e.start_time AS eventStartTime,
            e.end_time AS eventEndTime,
            e.category AS eventCategory,
            e.img_url AS eventImageUrl,

            b.id AS buildingId,
            b.name AS buildingName,

            f.id AS floorId,
            f.name AS floorName,
            f.level AS floorLevel
        FROM events e
        LEFT JOIN buildings b ON b.venue_id = e.venue_id
        LEFT JOIN floors f ON f.building_id = b.id
        WHERE e.venue_id = :venueId
        ORDER BY e.start_time, b.name, f.level
    """)
    fun getEventBuildingFloor(venueId: Long): Flow<List<EventBuildingFloorDto>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventWithVendors(eventId: Long): EventWithVendors?


}