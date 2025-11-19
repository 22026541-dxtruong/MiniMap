package ie.app.minimap.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.minimap.data.local.repository.EventRepository
import ie.app.minimap.data.local.repository.MapRepository
import ie.app.minimap.data.local.repository.VenueRepository
import ie.app.minimap.data.local.repository.impl.EventRepositoryImpl
import ie.app.minimap.data.local.repository.impl.MapRepositoryImpl
import ie.app.minimap.data.local.repository.impl.VenueRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMapRepository(
        impl: MapRepositoryImpl
    ): MapRepository

    @Binds
    @Singleton
    abstract fun bindVenueRepository(
        impl: VenueRepositoryImpl
    ): VenueRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(
        impl: EventRepositoryImpl
    ): EventRepository


}