package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.FavouriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeFavourites(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    suspend fun getFavourites(): List<FavouriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE mediaUri = :mediaUri)")
    fun observeIsFavourite(mediaUri: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE mediaUri = :mediaUri)")
    suspend fun isFavourite(mediaUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavourite(favourite: FavouriteEntity): Long

    @Query("DELETE FROM favourites WHERE mediaUri = :mediaUri")
    suspend fun deleteFavouriteByUri(mediaUri: String): Int

    @Query("DELETE FROM favourites WHERE mediaId = :mediaId")
    suspend fun deleteFavouriteById(mediaId: String): Int

    @Query("DELETE FROM favourites")
    suspend fun clearFavourites(): Int

    @Query("SELECT COUNT(*) FROM favourites")
    suspend fun getCount(): Int
}
