package com.mpx.player.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mpx.player.data.local.dao.FavouriteDao
import com.mpx.player.data.local.dao.PlaybackHistoryDao
import com.mpx.player.data.local.dao.PlaybackResumeDao
import com.mpx.player.data.local.dao.PlaylistDao
import com.mpx.player.data.local.entity.CustomPlaylistEntity
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
    ],
    version = 1,
    exportSchema = false,
)
abstract class MpxDatabase : RoomDatabase() {

    abstract fun favouriteDao(): FavouriteDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackResumeDao(): PlaybackResumeDao

    companion object {
        private const val DATABASE_NAME = "mpx_player.db"

        @Volatile
        private var instance: MpxDatabase? = null

        fun getInstance(context: Context): MpxDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MpxDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
