package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        EpisodeEntity::class,
        MyListEntity::class,
        WatchHistoryEntity::class,
        AppSettingsEntity::class,
        ChannelEntity::class,
        FeaturedMediaEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        BackupHistoryEntity::class,
        TmdbAutoSyncHistoryEntity::class,
        DownloadEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playFilmeDao(): PlayFilmeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "playfilme_plus.db"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
