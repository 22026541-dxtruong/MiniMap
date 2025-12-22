package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {

    @Query("SELECT * from vendors WHERE id = :id")
    fun getVendorById(id: Long): Flow<Vendor>

    @Query("SELECT * from vendors")
    fun getAllVendors(): Flow<List<Vendor>>

    @Query("SELECT * from vendors WHERE venue_id = :venueId")
    suspend fun getVendorsByVenueId(venueId: Long): List<Vendor>

    @Upsert
    suspend fun upsert(vendor: Vendor): Long

    @Upsert
    suspend fun upsert(vendors: List<Vendor>)

    @Delete
    suspend fun delete(vendor: Vendor)
}