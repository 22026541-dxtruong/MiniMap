package ie.app.minimap.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor

data class BuildingWithFloors(
    @Embedded
    val building: Building,
    @Relation(
        parentColumn = "id",
        entityColumn = "building_id"
    )
    val floors: List<Floor>
)
