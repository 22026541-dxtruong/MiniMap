package ie.app.minimap.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Vendor

/**
 * Relation: Vendor kèm danh sách Booth
 */
data class VendorWithBooths(
    @Embedded val vendor: Vendor,

    @Relation(
        parentColumn = "id",      // Vendor.id
        entityColumn = "vendor_id" // Booth.vendorId
    )
    val booths: List<Booth>
)

/**
 * Relation: Event kèm danh sách VendorWithBooths
 */
//data class EventWithVendors(
//    @Embedded val event: Event,
//
//    @Relation(
//        entity = Booth::class,
//        parentColumn = "id",         // Event.id
//        entityColumn = "event_id", // Vendor.eventId
//    )
//    val vendors: List<VendorWithBooths>
//)