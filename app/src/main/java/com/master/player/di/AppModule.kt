package com.maxter.player.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.maxter.player.data.local.MaxterDao
import com.maxter.player.data.local.MaxterDatabase
import com.maxter.player.data.repository.MediaScannerRepository
import com.maxter.player.player.MaxterPlayerController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMaxterDatabase(
        @ApplicationContext context: Context
    ): MaxterDatabase {
        return Room.databaseBuilder(
            context,
            MaxterDatabase::class.java,
            "maxter_player.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMaxterDao(database: MaxterDatabase): MaxterDao {
        return database.dao()
    }

    @Provides
    @Singleton
    fun provideMediaScannerRepository(
        @ApplicationContext context: Context,
        dao: MaxterDao
    ): MediaScannerRepository {
        return MediaScannerRepository(context, dao)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMaxterPlayerController(
        @ApplicationContext context: Context
    ): MaxterPlayerController {
        return MaxterPlayerController(context)
    }
}
