package ie.app.minimap.di

import android.app.Application
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.minimap.data.local.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(application: Application) = Room
        .databaseBuilder(
            application, AppDatabase::class.java, "minimap_database"
        )
        .fallbackToDestructiveMigration(true) // 💣 Xóa DB cũ nếu version hoặc schema thay đổi
        .build()

    @Provides
    @Singleton
    fun provideNodeDao(db: AppDatabase) = db.nodeDao()

}