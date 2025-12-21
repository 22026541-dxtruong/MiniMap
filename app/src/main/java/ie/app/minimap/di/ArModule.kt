package ie.app.minimap.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import ie.app.minimap.ui.ar.ArSessionManager

@Module
@InstallIn(ViewModelComponent::class)
object ArModule {

    @Provides
    @ViewModelScoped
    fun provideArSessionManager(application: Application): ArSessionManager = ArSessionManager(application)

}