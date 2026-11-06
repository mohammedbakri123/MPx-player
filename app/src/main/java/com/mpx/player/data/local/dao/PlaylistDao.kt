package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mpx.player.data.local.entity.CustomPlaylistEntity
import com.mpx.player.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithItemCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isVideoPlaylist: Boolean,
    val itemCount: Int,
)

@Dao
interface PlaylistDao {

    @Query("""
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.isVideoPlaylist,
               (SELECT COUNT(*) FROM playlist_items WHERE playlistId = p.id) AS itemCount
        FROM custom_playlists p
        ORDER BY p.updatedAt DESC
    """)
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithItemCount>>

    @Query("""
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.isVideoPlaylist,
               (SELECT COUNT(*) FROM playlist_items WHERE playlistId = p.id) AS itemCount
        FROM custom_playlists p
        ORDER BY p.updatedAt DESC
    """)
    suspend fun getPlaylistsWithCount(): List<PlaylistWithItemCount>

    @Query("SELECT * FROM custom_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): CustomPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: CustomPlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: CustomPlaylistEntity): Int

    @Query("DELETE FROM custom_playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long): Int

    @Query("DELETE FROM custom_playlists")
    suspend fun clearPlaylists(): Int

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemEntity>

    // Issue #230: IGNORE, not REPLACE — a re-add of an item that is already
    // in the playlist must be a no-op (Room returns -1), not a delete +
    // re-insert that silently moves the item to the end of the list.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deletePlaylistItem(itemId: Long): Int

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String): Int

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getNextOrderIndex(playlistId: Long): Int
}
