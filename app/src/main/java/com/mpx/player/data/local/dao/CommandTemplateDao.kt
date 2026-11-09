package com.mpx.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpx.player.data.local.entity.CommandTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for saved custom-command templates (issue #410). Templates
 * are a small set, newest first; the templates page and the
 * custom-command editor's picker both observe the same flow.
 */
@Dao
interface CommandTemplateDao {
    /** Newest first, for the templates page and the picker. */
    @Query("SELECT * FROM command_templates ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CommandTemplateEntity>>

    @Query("SELECT * FROM command_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CommandTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: CommandTemplateEntity)

    @Query("DELETE FROM command_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
