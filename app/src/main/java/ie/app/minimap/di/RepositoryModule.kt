package ie.app.minimap.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.minimap.data.local.repository.EdgeRepository
import ie.app.minimap.data.local.repository.EventRepository
import ie.app.minimap.data.local.repository.FloorConnectionRepository
import ie.app.minimap.data.local.repository.FloorRepository
import ie.app.minimap.data.local.repository.NodeRepository
import ie.app.minimap.data.local.repository.VenueRepository
import ie.app.minimap.data.local.repository.impl.EdgeRepositoryImpl
import ie.app.minimap.data.local.repository.impl.EventRepositoryImpl
import ie.app.minimap.data.local.repository.impl.FloorConnectionRepositoryImpl
import ie.app.minimap.data.local.repository.impl.FloorRepositoryImpl
import ie.app.minimap.data.local.repository.impl.NodeRepositoryImpl
import ie.app.minimap.data.local.repository.impl.VenueRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindVenueRepository(
        impl: VenueRepositoryImpl
    ): VenueRepository

    @Binds
    @Singleton
    abstract fun bindNodeRepository(
        impl: NodeRepositoryImpl
    ): NodeRepository

    @Binds
    @Singleton
    abstract fun bindFloorRepository(
        impl: FloorRepositoryImpl
    ): FloorRepository

    @Binds
    @Singleton
    abstract fun bindEdgeRepository(
        impl: EdgeRepositoryImpl
    ): EdgeRepository

    @Binds
    @Singleton
    abstract fun bindFloorConnectionRepository(
        impl: FloorConnectionRepositoryImpl
    ): FloorConnectionRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(
        impl: EventRepositoryImpl
    ): EventRepository


}