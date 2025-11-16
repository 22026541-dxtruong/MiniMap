package ie.app.minimap.data.local.dto

import androidx.room.ColumnInfo

data class EventBuildingFloorDto(
    @ColumnInfo(name = "eventId")
    val eventId: Int,

    @ColumnInfo(name = "eventName")
    val eventName: String?,

    @ColumnInfo(name = "img_url")
    val eventImageUrl: String?,

    @ColumnInfo(name = "buildingId")
    val buildingId: Int?,

    @ColumnInfo(name = "buildingName")
    val buildingName: String?,

    @ColumnInfo(name = "floorId")
    val floorId: Int?,

    @ColumnInfo(name = "floorName")
    val floorName: String?,

    @ColumnInfo(name = "floorLevel")
    val floorLevel: Int?
)