package ie.app.minimap.data.local.dto

import androidx.room.ColumnInfo

data class EventBuildingFloorDto(
    val eventId: Long,
    val eventName: String?,
    val eventImageUrl: String?,
    val buildingId: Long?,
    val buildingName: String?,
    val floorId: Long?,
    val floorName: String?,
    val floorLevel: Long?
)
