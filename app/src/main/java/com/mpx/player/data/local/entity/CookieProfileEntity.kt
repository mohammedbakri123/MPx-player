package com.mpx.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for one saved cookie profile (issue #409): the cookies
 * from a successful in-app sign-in, kept so the next restricted
 * download from the same site does not need another sign-in. Seal
 * keeps the same record as `CookieProfile` (id, url, content —
 * JunkFood02/Seal, GPL-3.0, `database/objects/CookieProfile.kt`);
 * MPx adds a display [name] (default: the site's host) and a
 * [createdAt] so the list orders newest first.
 *
 * [content] is the Netscape-format cookies text exactly as
 * [com.mpx.player.download.CookieImporter] writes it — the downloader
 * writes it back to a fresh staging file each time the profile is
 * used, so the stored text never needs parsing. Re-saving the same
 * site replaces the old row (see the DAO), because cookies go stale
 * and the newest login is the one that works.
 */
@Entity(tableName = "cookie_profiles")
data class CookieProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
