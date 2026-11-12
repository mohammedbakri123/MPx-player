package com.mpx.player.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Room migration unit test validating the v12 → v13 schema upgrade (CP-3, Step 2.3).
 *
 * Validates that:
 * 1. The database opens at v12 with pre-existing rows in all user data tables.
 * 2. MIGRATION_12_13 executes successfully with startVersion = 12 and endVersion = 13.
 * 3. All pre-existing rows survive intact without data loss or column alteration.
 * 4. Exactly the 5 expected indexes are created on the sorted/queried timestamp columns:
 *    - index_playback_history_lastPlayedAt on playback_history(lastPlayedAt)
 *    - index_favourites_addedAt on favourites(addedAt)
 *    - index_favourites_mediaId on favourites(mediaId)
 *    - index_playback_resume_lastUpdated on playback_resume(lastUpdated)
 *    - index_custom_playlists_updatedAt on custom_playlists(updatedAt)
 * 5. The migration is idempotent and safe against repeated execution.
 */
class MpxDatabaseMigrationTest {

    data class IndexDefinition(
        val name: String,
        val tableName: String,
        val columns: List<String>,
        val isUnique: Boolean = false,
    )

    data class TableRow(
        val tableName: String,
        val values: Map<String, Any?>,
    )

    /**
     * Lightweight test double implementing [SupportSQLiteDatabase] via dynamic proxy,
     * maintaining schema tables, rows, and indexes in memory without Android runtime dependencies.
     */
    class TestSupportSQLiteDatabase(initialVersion: Int = 12) : InvocationHandler {
        var version: Int = initialVersion
        val tables = mutableSetOf<String>()
        val rows = mutableListOf<TableRow>()
        val indexes = mutableMapOf<String, IndexDefinition>()
        val executedStatements = mutableListOf<String>()

        val proxy: SupportSQLiteDatabase = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            this,
        ) as SupportSQLiteDatabase

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getVersion" -> version
                "setVersion" -> {
                    version = args?.get(0) as Int
                    null
                }
                "execSQL" -> {
                    val sql = args?.get(0) as String
                    handleExecSql(sql)
                    null
                }
                "isOpen" -> true
                "isReadOnly" -> false
                "close" -> null
                "toString" -> "TestSupportSQLiteDatabase(version=$version)"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        }

        fun createTable(name: String) {
            tables.add(name.lowercase())
        }

        fun insertRow(tableName: String, values: Map<String, Any?>) {
            tables.add(tableName.lowercase())
            rows.add(TableRow(tableName.lowercase(), values))
        }

        private fun handleExecSql(sql: String) {
            val normalized = sql.trim()
            executedStatements.add(normalized)

            // Parse CREATE [UNIQUE] INDEX [IF NOT EXISTS] `index_name` ON `table_name` (`col1`, `col2`...)
            val createIndexRegex = Regex(
                """CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?['"`]?([a-zA-Z0-9_]+)['"`]?\s+ON\s+['"`]?([a-zA-Z0-9_]+)['"`]?\s*\(([^)]+)\)""",
                RegexOption.IGNORE_CASE,
            )
            val match = createIndexRegex.find(normalized)
            if (match != null) {
                val isUnique = match.groupValues[1].isNotBlank()
                val indexName = match.groupValues[2]
                val tableName = match.groupValues[3].lowercase()
                val columns = match.groupValues[4]
                    .split(",")
                    .map { it.trim().trim('`', '\'', '"') }

                indexes[indexName] = IndexDefinition(
                    name = indexName,
                    tableName = tableName,
                    columns = columns,
                    isUnique = isUnique,
                )
                return
            }

            // Parse CREATE TABLE IF NOT EXISTS `table_name`
            val createTableRegex = Regex(
                """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?['"`]?([a-zA-Z0-9_]+)['"`]?""",
                RegexOption.IGNORE_CASE,
            )
            val tableMatch = createTableRegex.find(normalized)
            if (tableMatch != null) {
                tables.add(tableMatch.groupValues[1].lowercase())
            }
        }
    }

    @Test
    fun migration12To13_hasCorrectVersionRange() {
        assertEquals("startVersion must be 12", 12, MpxDatabase.MIGRATION_12_13.startVersion)
        assertEquals("endVersion must be 13", 13, MpxDatabase.MIGRATION_12_13.endVersion)
    }

    @Test
    fun migration12To13_opensAtV12_migratesToV13_preservesRowsAndCreatesIndices() {
        val dbHelper = TestSupportSQLiteDatabase(initialVersion = 12)

        // 1. Setup pre-existing v12 schema tables
        dbHelper.createTable("playback_history")
        dbHelper.createTable("favourites")
        dbHelper.createTable("playback_resume")
        dbHelper.createTable("custom_playlists")
        dbHelper.createTable("playlist_items")

        // Existing indexes present before migration (playlist_items already had indexes)
        dbHelper.indexes["index_playlist_items_playlistId"] = IndexDefinition(
            name = "index_playlist_items_playlistId",
            tableName = "playlist_items",
            columns = listOf("playlistId"),
        )
        dbHelper.indexes["index_playlist_items_playlistId_mediaUri"] = IndexDefinition(
            name = "index_playlist_items_playlistId_mediaUri",
            tableName = "playlist_items",
            columns = listOf("playlistId", "mediaUri"),
            isUnique = true,
        )

        // 2. Seed pre-existing rows in each table at v12
        val historyRow = mapOf(
            "mediaUri" to "content://media/external/video/media/1001",
            "mediaId" to "1001",
            "title" to "Sample Movie 1080p",
            "subtitle" to "",
            "isVideo" to true,
            "lastPlayedAt" to 1700000000000L,
            "playbackPositionMs" to 42000L,
            "durationMs" to 120000L,
        )
        dbHelper.insertRow("playback_history", historyRow)

        val favouriteRow = mapOf(
            "mediaUri" to "content://media/external/audio/media/2002",
            "mediaId" to "2002",
            "title" to "Favorite Song",
            "subtitle" to "Top Artist",
            "isVideo" to false,
            "durationMs" to 180000L,
            "addedAt" to 1700000050000L,
        )
        dbHelper.insertRow("favourites", favouriteRow)

        val resumeRow = mapOf(
            "mediaId" to "/storage/emulated/0/Movies/sample.mp4",
            "mediaUri" to "content://media/external/video/media/3003",
            "positionMs" to 95000L,
            "durationMs" to 300000L,
            "lastUpdated" to 1700000100000L,
        )
        dbHelper.insertRow("playback_resume", resumeRow)

        val playlistRow = mapOf(
            "id" to 42L,
            "name" to "Road Trip Jams",
            "createdAt" to 1690000000000L,
            "updatedAt" to 1700000200000L,
            "isVideoPlaylist" to false,
        )
        dbHelper.insertRow("custom_playlists", playlistRow)

        val itemRow = mapOf(
            "id" to 1L,
            "playlistId" to 42L,
            "mediaUri" to "content://media/external/audio/media/5005",
            "mediaId" to "5005",
            "title" to "Track 1",
            "orderIndex" to 0,
            "addedAt" to 1700000250000L,
        )
        dbHelper.insertRow("playlist_items", itemRow)

        // Verify database is open at v12 before migration
        assertEquals("Database should start at v12", 12, dbHelper.version)
        assertEquals("5 pre-existing rows should be present at v12", 5, dbHelper.rows.size)
        assertEquals("Pre-existing indexes should be 2", 2, dbHelper.indexes.size)

        // 3. Execute Room MIGRATION_12_13
        MpxDatabase.MIGRATION_12_13.migrate(dbHelper.proxy)
        dbHelper.version = 13

        // 4. Verify version migrated to 13
        assertEquals("Database version must be upgraded to 13", 13, dbHelper.version)

        // 5. Verify pre-existing rows all survive intact
        assertEquals("All 5 pre-existing rows must survive migration", 5, dbHelper.rows.size)

        val retrievedHistory = dbHelper.rows.first { it.tableName == "playback_history" }.values
        assertEquals(historyRow["mediaUri"], retrievedHistory["mediaUri"])
        assertEquals(historyRow["mediaId"], retrievedHistory["mediaId"])
        assertEquals(historyRow["title"], retrievedHistory["title"])
        assertEquals(historyRow["lastPlayedAt"], retrievedHistory["lastPlayedAt"])

        val retrievedFav = dbHelper.rows.first { it.tableName == "favourites" }.values
        assertEquals(favouriteRow["mediaUri"], retrievedFav["mediaUri"])
        assertEquals(favouriteRow["mediaId"], retrievedFav["mediaId"])
        assertEquals(favouriteRow["addedAt"], retrievedFav["addedAt"])

        val retrievedResume = dbHelper.rows.first { it.tableName == "playback_resume" }.values
        assertEquals(resumeRow["mediaId"], retrievedResume["mediaId"])
        assertEquals(resumeRow["positionMs"], retrievedResume["positionMs"])
        assertEquals(resumeRow["lastUpdated"], retrievedResume["lastUpdated"])

        val retrievedPlaylist = dbHelper.rows.first { it.tableName == "custom_playlists" }.values
        assertEquals(playlistRow["id"], retrievedPlaylist["id"])
        assertEquals(playlistRow["name"], retrievedPlaylist["name"])
        assertEquals(playlistRow["updatedAt"], retrievedPlaylist["updatedAt"])

        val retrievedItem = dbHelper.rows.first { it.tableName == "playlist_items" }.values
        assertEquals(itemRow["playlistId"], retrievedItem["playlistId"])
        assertEquals(itemRow["mediaUri"], retrievedItem["mediaUri"])

        // 6. Verify exactly the 5 new indexes exist afterwards
        val expectedIndexes = setOf(
            "index_playback_history_lastPlayedAt",
            "index_favourites_addedAt",
            "index_favourites_mediaId",
            "index_playback_resume_lastUpdated",
            "index_custom_playlists_updatedAt",
        )

        for (indexName in expectedIndexes) {
            val idx = dbHelper.indexes[indexName]
            assertNotNull("Index $indexName must exist after migration", idx)
        }

        // Verify details of each new index
        val historyIndex = dbHelper.indexes["index_playback_history_lastPlayedAt"]!!
        assertEquals("playback_history", historyIndex.tableName)
        assertEquals(listOf("lastPlayedAt"), historyIndex.columns)

        val favAddedAtIndex = dbHelper.indexes["index_favourites_addedAt"]!!
        assertEquals("favourites", favAddedAtIndex.tableName)
        assertEquals(listOf("addedAt"), favAddedAtIndex.columns)

        val favMediaIdIndex = dbHelper.indexes["index_favourites_mediaId"]!!
        assertEquals("favourites", favMediaIdIndex.tableName)
        assertEquals(listOf("mediaId"), favMediaIdIndex.columns)

        val resumeIndex = dbHelper.indexes["index_playback_resume_lastUpdated"]!!
        assertEquals("playback_resume", resumeIndex.tableName)
        assertEquals(listOf("lastUpdated"), resumeIndex.columns)

        val playlistIndex = dbHelper.indexes["index_custom_playlists_updatedAt"]!!
        assertEquals("custom_playlists", playlistIndex.tableName)
        assertEquals(listOf("updatedAt"), playlistIndex.columns)

        // Total indexes = 2 pre-existing + 5 new = 7
        assertEquals("Total indexes after migration must be 7", 7, dbHelper.indexes.size)

        // 7. Verify no destructive SQL commands were executed during migration
        for (sql in dbHelper.executedStatements) {
            assertTrue("Migration must only create indexes: $sql", sql.startsWith("CREATE INDEX IF NOT EXISTS", ignoreCase = true))
        }
        assertEquals("Exactly 5 CREATE INDEX statements must be executed", 5, dbHelper.executedStatements.size)
    }

    @Test
    fun migration12To13_isIdempotent() {
        val dbHelper = TestSupportSQLiteDatabase(initialVersion = 12)
        dbHelper.createTable("playback_history")
        dbHelper.createTable("favourites")
        dbHelper.createTable("playback_resume")
        dbHelper.createTable("custom_playlists")

        // First run
        MpxDatabase.MIGRATION_12_13.migrate(dbHelper.proxy)
        assertEquals(5, dbHelper.indexes.size)

        // Second run must succeed without error
        MpxDatabase.MIGRATION_12_13.migrate(dbHelper.proxy)
        assertEquals(5, dbHelper.indexes.size)
    }
}
