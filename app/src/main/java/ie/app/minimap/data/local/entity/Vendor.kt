package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.VendorProto

@Entity(
    tableName = "vendors"
)
data class Vendor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    val name: String = "Vendor",
    val description: String = "Description",
    val category: String = "Category",
    @ColumnInfo(name = "contact_info")
    val contactInfo: String = "",
    @ColumnInfo(name = "logo_url")
    val logoUrl: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor(proto: VendorProto) : this(
        id = proto.id,
        venueId = proto.venueId,
        name = proto.name,
        description = proto.description,
        category = proto.category,
        contactInfo = proto.contactInfo,
        logoUrl = proto.logoUrl,
        createdAt = proto.createdAt
    )
}

fun Vendor.toProto(): VendorProto {
    return VendorProto.newBuilder()
        .setId(id)
        .setVenueId(venueId)
        .setName(name)
        .setDescription(description)
        .setCategory(category)
        .setContactInfo(contactInfo)
        .setLogoUrl(logoUrl)
        .setCreatedAt(createdAt)
        .build()
}
