package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "venues"
)
data class Venue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val description: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
