package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendors"
)
data class Vendor(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val category: String = "",
    @ColumnInfo(name = "contact_info")
    val contactInfo: String = "",
    @ColumnInfo(name = "logo_url")
    val logoUrl: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)