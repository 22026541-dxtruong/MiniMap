package ie.app.minimap.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.minimap.data.local.AppDatabase
import ie.app.minimap.data.local.dao.BoothDao
import ie.app.minimap.data.local.dao.BuildingDao
import ie.app.minimap.data.local.dao.EdgeDao
import ie.app.minimap.data.local.dao.EventDao
import ie.app.minimap.data.local.dao.FloorConnectionDao
import ie.app.minimap.data.local.dao.FloorDao
import ie.app.minimap.data.local.dao.NodeDao
import ie.app.minimap.data.local.dao.ShapeDao
import ie.app.minimap.data.local.dao.VendorDao
import ie.app.minimap.data.local.dao.VenueDao
import ie.app.minimap.data.local.repository.EventRepository
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import ie.app.minimap.data.local.repository.VenueRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMapRepository(
        buildingDao: BuildingDao,
        nodeDao: NodeDao,
        edgeDao: EdgeDao,
        floorDao: FloorDao,
        floorConnectionDao: FloorConnectionDao,
        shapeDao: ShapeDao
    ): MapRepository = MapRepository(
        buildingDao,
        floorDao,
        nodeDao,
        edgeDao,
        floorConnectionDao,
        shapeDao
    )

    @Provides
    @Singleton
    fun provideVenueRepository(
        venueDao: VenueDao,
        buildingDao: BuildingDao,
        floorDao: FloorDao,
        database: AppDatabase
    ): VenueRepository = VenueRepository(
        venueDao,
        buildingDao,
        floorDao,
        database
    )

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: EventDao,
        boothDao: BoothDao,
        buildingDao: BuildingDao,
        vendorDao: VendorDao,
    ): EventRepository = EventRepository(eventDao, boothDao, buildingDao,vendorDao)

    @Provides
    @Singleton
    fun provideInfoRepository(
        db: AppDatabase,
        vendorDao: VendorDao,
        boothDao: BoothDao,
        nodeDao: NodeDao,
        eventDao: EventDao,
        buildingDao: BuildingDao,
        floorDao: FloorDao,
        floorConnectionDao: FloorConnectionDao,
        edgeDao: EdgeDao,
        venueDao: VenueDao,
        shapeDao: ShapeDao
    ): InfoRepository = InfoRepository(
        db,
        venueDao,
        vendorDao,
        boothDao,
        nodeDao,
        buildingDao,
        floorDao,
        floorConnectionDao,
        edgeDao,
        eventDao,
        shapeDao
    )

}