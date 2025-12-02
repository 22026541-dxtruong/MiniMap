package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Transaction
    @Query("""
    SELECT 
        e.*,
        b.name AS boothName,
        bd.name AS buildingName,
        f.name AS floorName
    FROM events e
    LEFT JOIN booths b ON e.booth_id = b.id
    LEFT JOIN buildings bd ON b.building_id = bd.id
    LEFT JOIN floors f ON b.floor_id = f.id
    WHERE e.venue_id = :venueId
    ORDER BY e.start_time ASC
    """)
    fun getEventWithBuildingAndFloor(venueId: Long): Flow<List<EventWithBuildingAndFloor>>

//    @Query("SELECT * FROM events WHERE id = :eventId")
//    fun getEventWithVendors(eventId: Long): EventWithVendors?

}