package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.flow.Flow

@Dao
interface VenueDao {
    @Query("SELECT * from venues WHERE id = :id")
    fun getVenueById(id: Long): Flow<Venue>

    @Query("SELECT * from venues")
    fun getAllVenues(): Flow<List<Venue>>

    @Upsert
    suspend fun upsert(venue: Venue): Long

    @Delete
    suspend fun delete(venue: Venue)

}