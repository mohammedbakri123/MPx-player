package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.CookieProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved cookie profiles (issue #409). Profiles are a small
 * set, newest first; the Download tab and the profiles page both
 * observe the same flow.
 */
@Dao
interface CookieProfileDao {
    /** Newest first, for the picker and the profiles page. */
    @Query("SELECT * FROM cookie_profiles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CookieProfileEntity>>

    @Query("SELECT * FROM cookie_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CookieProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CookieProfileEntity)

    @Query("DELETE FROM cookie_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Re-saving a site replaces its old profile — cookies go stale. */
    @Query("DELETE FROM cookie_profiles WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}
