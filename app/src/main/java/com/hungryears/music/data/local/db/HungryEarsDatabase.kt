package com.hungryears.music.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hungryears.music.data.local.db.dao.LibraryDao
import com.hungryears.music.data.local.db.dao.PlaylistDao
import com.hungryears.music.data.local.db.dao.SearchDao
import com.hungryears.music.data.local.db.entity.FavouriteEntity
import com.hungryears.music.data.local.db.entity.HistoryEntity
import com.hungryears.music.data.local.db.entity.PlaylistEntity
import com.hungryears.music.data.local.db.entity.PlaylistTrackEntity
import com.hungryears.music.data.local.db.entity.RecentQueryEntity
import com.hungryears.music.data.local.db.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        FavouriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        RecentQueryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HungryEarsDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun searchDao(): SearchDao

    companion object {
        private const val DB_NAME = "hungry_ears.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_queries` (
                        `query` TEXT NOT NULL,
                        `normalized` TEXT NOT NULL,
                        `searched_at` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): HungryEarsDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder(
                appContext,
                HungryEarsDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .setDriver(BundledSQLiteDriver())
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            FtsIndex.buildIfNeeded(appContext, db)
                        }

                        override fun onOpen(connection: SQLiteConnection) {
                            super.onOpen(connection)
                            FtsIndex.buildIfNeeded(appContext, connection)
                        }
                    },
                )
                .build()
        }
    }
}
