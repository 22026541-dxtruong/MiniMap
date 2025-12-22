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
object DatabaseModule {

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

    @Provides
    @Singleton
    fun provideFloorDao(db: AppDatabase) = db.floorDao()

    @Provides
    @Singleton
    fun provideEdgeDao(db: AppDatabase) = db.edgeDao()

    @Provides
    @Singleton
    fun provideFloorConnectionDao(db: AppDatabase) = db.floorConnectionDao()

    @Provides
    @Singleton
    fun provideVenueDao(db: AppDatabase) = db.venueDao()

    @Provides
    @Singleton
    fun provideEventDao(db: AppDatabase) = db.eventDao()

    @Provides
    @Singleton
    fun provideBuildingDao(db: AppDatabase) = db.buildingDao()

    @Provides
    @Singleton
    fun provideVendorDao(db: AppDatabase) = db.vendorDao()

    @Provides
    @Singleton
    fun provideBoothDao(db: AppDatabase) = db.boothDao()

    @Provides
    @Singleton
    fun provideShapeDao(db: AppDatabase) = db.shapeDao()

}