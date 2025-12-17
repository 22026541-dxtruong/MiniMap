package ie.app.minimap.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Vendor

data class BoothWithVendor(
    @Embedded
    val booth: Booth,
    @Relation(
        parentColumn = "vendor_id",
        entityColumn = "id"
    )
    val vendor: Vendor
)