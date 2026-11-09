package com.mpx.player.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpx.player.data.local.dao.CommandTemplateDao
import com.mpx.player.data.local.dao.CookieProfileDao
import com.mpx.player.data.local.dao.DownloadHistoryDao
import com.mpx.player.data.local.dao.DownloadTaskDao
import com.mpx.player.data.local.dao.FavouriteDao
import com.mpx.player.data.local.dao.PlaybackHistoryDao
import com.mpx.player.data.local.dao.PlaybackResumeDao
import com.mpx.player.data.local.dao.PlaylistDao
import com.mpx.player.data.local.entity.CommandTemplateEntity
import com.mpx.player.data.local.entity.CookieProfileEntity
import com.mpx.player.data.local.entity.CustomPlaylistEntity
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.data.local.entity.DownloadTaskEntity
import com.mpx.player.data.local.entity.FavouriteEntity
import com.mpx.player.data.local.entity.PlaybackHistoryEntity
import com.mpx.player.data.local.entity.PlaybackResumeEntity
import com.mpx.player.data.local.entity.PlaylistItemEntity

/**
 * Main Room SQLite database for MPx Player.
 * Persists favourites, playback history, custom playlists, media resume positions,
 * the download queue (#342) and the download history (#346).
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
        DownloadHistoryEntity::class,
        CookieProfileEntity::class,
        CommandTemplateEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class MpxDatabase : RoomDatabase() {

    abstract fun favouriteDao(): FavouriteDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackResumeDao(): PlaybackResumeDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao
    abstract fun cookieProfileDao(): CookieProfileDao
    abstract fun commandTemplateDao(): CommandTemplateDao

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

        /**
         * Issue #346: the download_history table. Same rule as #342 —
         * a proper migration, so the 2→3 jump must NOT wipe the user's
         * favourites, history and playlists through the pre-existing
         * fallbackToDestructiveMigration() below. Column order/types
         * match the entity exactly, as Room validates.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `download_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`videoId` TEXT, `extractor` TEXT, " +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`uploader` TEXT, `thumbnailUrl` TEXT, " +
                        "`fileUri` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`fileSize` INTEGER NOT NULL, `isPublic` INTEGER NOT NULL, " +
                        "`isSdCard` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * Issue #383: the per-video format selector on the
         * download_tasks table. A nullable column — existing rows
         * keep null (global settings), exactly the right default.
         * Same rule as #342/#346: a proper migration, so the 3→4
         * jump must NOT wipe the user's favourites, history and
         * playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `formatId` TEXT")
            }
        }

        /**
         * Issue #386: the clips column on the download_tasks table.
         * Nullable — existing rows keep null (whole video), exactly
         * the right default. Same rule as #342/#346/#383: a proper
         * migration, so the 4→5 jump must NOT wipe the user's
         * favourites, history and playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `clips` TEXT")
            }
        }

        /**
         * Issue #387: the split-by-chapter column on the
         * download_tasks table. NOT NULL DEFAULT 0 — existing rows
         * keep one file, exactly the right default. Same rule as
         * #342/#346/#383/#386: a proper migration, so the 5→6 jump
         * must NOT wipe the user's favourites, history and
         * playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `splitByChapter` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Issue #388: the rename column on the download_tasks table.
         * Nullable — existing rows keep null (the video's own
         * title), exactly the right default. Same rule as
         * #342/#346/#383/#386/#387: a proper migration, so the 6→7
         * jump must NOT wipe the user's favourites, history and
         * playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `newTitle` TEXT")
            }
        }

        /**
         * Issue #389: the playlist-title column on the download_tasks
         * table. Nullable — existing rows keep null (single videos),
         * exactly the right default. Same rule as
         * #342/#346/#383/#386/#387/#388: a proper migration, so the
         * 7→8 jump must NOT wipe the user's favourites, history and
         * playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `playlistTitle` TEXT")
            }
        }

        /**
         * Issue #393: the SD-card flag on the download_history table.
         * NOT NULL DEFAULT 0 — existing rows are public-Downloads
         * files, exactly the right default. Same rule as
         * #342/#346/#383/#386/#387/#388/#389: a proper migration, so
         * the 8→9 jump must NOT wipe the user's favourites, history
         * and playlists through the pre-existing
         * fallbackToDestructiveMigration() below.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_history` ADD COLUMN `isSdCard` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Issue #409: the cookie_profiles table. A new table — no
         * existing rows to preserve — so the migration only creates
         * it. Column order/types match the entity exactly, as Room
         * validates. Same rule as #342/#346/#383/#386/#387/#388/#389/
         * #393: a proper migration, so the 9→10 jump must NOT wipe
         * the user's favourites, history and playlists through the
         * pre-existing fallbackToDestructiveMigration() below.
         */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cookie_profiles` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * Issue #410: the command_templates table. A new table — no
         * existing rows to preserve — so the migration only creates
         * it. Column order/types match the entity exactly, as Room
         * validates. Same rule as #342/#346/#383/#386/#387/#388/#389/
         * #393/#409: a proper migration, so the 10→11 jump must NOT
         * wipe the user's favourites, history and playlists through
         * the pre-existing fallbackToDestructiveMigration() below.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `command_templates` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`template` TEXT NOT NULL, " +
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
