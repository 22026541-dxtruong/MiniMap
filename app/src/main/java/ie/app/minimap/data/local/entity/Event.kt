package ie.app.minimap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ie.app.minimap.data.proto.EventProto

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = Venue::class,
            parentColumns = ["id"],
            childColumns = ["venue_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Booth::class,
            parentColumns = ["id"],
            childColumns = ["booth_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "booth_id")
    val boothId: Long = 0,
    @ColumnInfo(name = "venue_id")
    val venueId: Long = 0,
    val name: String = "",
    val description: String = "",
    @ColumnInfo(name = "start_time")
    val startTime: String = "",
    @ColumnInfo(name = "end_time")
    val endTime: String = "",
    val category: String = "",
    @ColumnInfo(name = "img_url")
    val imgUrl: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor(proto: EventProto) : this(
        id = proto.id,
        boothId = proto.boothId,
        venueId = proto.venueId,
        name = proto.name,
        description = proto.description,
        startTime = proto.startTime,
        endTime = proto.endTime,
        category = proto.category,
        imgUrl = proto.imgUrl,
        createdAt = proto.createdAt
    )
}

fun Event.toProto(): EventProto {
    return EventProto.newBuilder()
        .setId(id)
        .setBoothId(boothId)
        .setVenueId(venueId)
        .setName(name)
        .setDescription(description)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setCategory(category)
        .setImgUrl(imgUrl)
        .setCreatedAt(createdAt)
        .build()
}
