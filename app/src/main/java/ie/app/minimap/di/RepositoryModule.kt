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
        floorConnectionDao: FloorConnectionDao
    ): MapRepository = MapRepository(
        buildingDao,
        floorDao,
        nodeDao,
        edgeDao,
        floorConnectionDao
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
        eventDao: EventDao
    ): EventRepository = EventRepository(eventDao)

    @Provides
    @Singleton
    fun provideInfoRepository(
        vendorDao: VendorDao,
        boothDao: BoothDao,
        nodeDao: NodeDao,
        eventDao: EventDao
    ): InfoRepository = InfoRepository(vendorDao, boothDao, nodeDao, eventDao)

}