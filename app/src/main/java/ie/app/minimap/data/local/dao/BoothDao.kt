package ie.app.minimap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import ie.app.minimap.data.local.entity.Booth
import kotlinx.coroutines.flow.Flow

@Dao
interface BoothDao {
    @Query("SELECT * from booths WHERE id = :id")
    fun getBoothById(id: Long): Flow<Booth>

    @Query("SELECT * from booths")
    fun getAllBooths(): Flow<List<Booth>>

    @Upsert
    suspend fun upsert(booth: Booth): Long

    @Delete
    suspend fun delete(booth: Booth)
}
