package ie.app.minimap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Event
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.entity.Venue

@Database(
    [
    Venue::class,
    Vendor::class,
    Node::class,
    Floor::class,
    FloorConnection::class,
    Building::class,
    Event::class,
    Edge::class,
    Booth::class
    ],
    version = 2,
    exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun venueDao() : VenueDao
    abstract fun floorDao() : FloorDao
    abstract fun floorConnectionDao() : FloorConnectionDao
    abstract fun edgeDao() : EdgeDao
    abstract fun EventDao() : EventDao
}