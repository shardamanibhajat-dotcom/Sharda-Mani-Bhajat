package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        VideoEntity::class,
        LikeEntity::class,
        CommentEntity::class,
        SubscriptionEntity::class,
        VideoViewEntity::class,
        NotificationEntity::class,
        BlockedUserEntity::class,
        ReportEntity::class,
        ErrorLogEntity::class,
        WatchHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        NotificationPreferencesEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class CreatorPaisaDatabase : RoomDatabase() {
    abstract fun dao(): CreatorPaisaDao

    companion object {
        @Volatile
        private var INSTANCE: CreatorPaisaDatabase? = null

        fun getInstance(context: Context): CreatorPaisaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): CreatorPaisaDatabase {
            lateinit var db: CreatorPaisaDatabase
            db = Room.databaseBuilder(
                appContext,
                CreatorPaisaDatabase::class.java,
                "creator_paisa_db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(supportDb: SupportSQLiteDatabase) {
                        super.onCreate(supportDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedIfNeeded(db.dao())
                        }
                    }

                    override fun onDestructiveMigration(dbSql: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(dbSql)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedIfNeeded(db.dao())
                        }
                    }
                })
                .build()
            return db
        }

        suspend fun seedIfNeeded(dao: CreatorPaisaDao) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                if (dao.getUserCount() > 0) return@withContext
                dao.insertUsersIgnore(SeedData.initialUsers())
                dao.insertVideosIgnore(SeedData.initialVideos())
                dao.insertCommentsIgnore(SeedData.initialComments())
                dao.insertNotificationsIgnore(SeedData.initialNotifications())
                SeedData.initialErrorLogs().forEach { dao.insertErrorLog(it) }
                dao.insertPlaylistsIgnore(SeedData.initialPlaylists())
                dao.insertPlaylistVideosIgnore(SeedData.initialPlaylistVideos())
            }
        }
    }
}
