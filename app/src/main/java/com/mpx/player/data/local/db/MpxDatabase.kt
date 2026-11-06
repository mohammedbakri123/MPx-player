package com.mpx.player.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpx.player.data.local.dao.DownloadTaskDao
import com.mpx.player.data.local.dao.FavouriteDao
import com.mpx.player.data.local.dao.PlaybackHistoryDao
import com.mpx.player.data.local.dao.PlaybackResumeDao
import com.mpx.player.data.local.dao.PlaylistDao
import com.mpx.player.data.local.entity.CustomPlaylistEntity
import com.mpx.player.data.local.entity.DownloadTaskEntity
import com.mpx.player.data.local.entity.FavouriteEntity
import com.mpx.player.data.local.entity.PlaybackHistoryEntity
import com.mpx.player.data.local.entity.PlaybackResumeEntity
import com.mpx.player.data.local.entity.PlaylistItemEntity

/**
 * Main Room SQLite database for MPx Player.
 * Persists favourites, playback history, custom playlists, and media resume positions.
 *
 * Fresh-install strategy:
 * Starts with empty tables on fresh installs (no mock data).
 * Fallback to destructive migration safely rebuilds the database on schema revisions during development.
 */
@Database(
    entities = [
        FavouriteEntity::class,
        PlaybackHistoryEntity::class,
        CustomPlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackResumeEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MpxDatabase : RoomDatabase() {

    abstract fun favouriteDao(): FavouriteDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackResumeDao(): PlaybackResumeDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        private const val DATABASE_NAME = "mpx_player.db"

        @Volatile
        private var instance: MpxDatabase? = null

        /**
         * Issue #342: the download_tasks table. A proper migration —
         * the 1→2 jump must NOT wipe the user's favourites, history
         * and playlists, which the pre-existing
         * fallbackToDestructiveMigration() below would otherwise do
         * on any unhandled version jump. The fallback stays as the
         * safety net for jumps this migration does not cover.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `download_tasks` (" +
                        "`id` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`error` TEXT, `audioOnly` INTEGER NOT NULL, " +
                        "`subtitles` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun getInstance(context: Context): MpxDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MpxDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
