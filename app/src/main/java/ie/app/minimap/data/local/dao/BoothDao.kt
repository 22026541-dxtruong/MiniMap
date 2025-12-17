package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.relations.BoothWithVendor
import kotlinx.coroutines.flow.Flow

@Dao
interface BoothDao {
    @Query("SELECT * from booths WHERE id = :id")
    fun getBoothById(id: Long): Flow<Booth>

    @Query("SELECT * from booths")
    fun getAllBooths(): Flow<List<Booth>>

    @Query("SELECT * from booths WHERE venue_id = :venueId")
    fun getBoothsByVenueId(venueId: Long): List<Booth>

    @Transaction
    @Query("SELECT * from booths WHERE node_id = :nodeId")
    suspend fun getBoothWithVendorByNodeId(nodeId: Long): BoothWithVendor?

    @Upsert
    suspend fun upsert(booth: Booth): Long

    @Upsert
    suspend fun upsert(booths: List<Booth>)

    @Delete
    suspend fun delete(booth: Booth)
}
