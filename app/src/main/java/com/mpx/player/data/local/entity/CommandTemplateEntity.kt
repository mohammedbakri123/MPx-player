package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for one saved custom-command template (issue #410):
 * a named yt-dlp command a user reuses instead of retyping it into
 * the single custom-command slot. Seal keeps the same record as
 * `CommandTemplate` (id, name, template — JunkFood02/Seal, GPL-3.0,
 * `database/objects/CommandTemplate.kt`); MPx adds a [createdAt] so
 * the list orders newest first.
 *
 * Picking a template fills the single command slot (see
 * [com.mpx.player.ui.screens.download.CustomCommandDialog]) — the
 * template IS the command once saved there, so the download path
 * needs no changes. The slot stays the one thing that runs.
 */
@Entity(tableName = "command_templates")
data class CommandTemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val template: String,
    val createdAt: Long = System.currentTimeMillis(),
)
