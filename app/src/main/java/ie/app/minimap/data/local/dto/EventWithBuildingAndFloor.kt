package ie.app.minimap.data.local.dto

import androidx.room.Embedded
import ie.app.minimap.data.local.entity.Event

data class EventWithBuildingAndFloor(
    @Embedded val event: Event,

    val boothName: String?,
    val buildingName: String?,
    val floorName: String?
)
