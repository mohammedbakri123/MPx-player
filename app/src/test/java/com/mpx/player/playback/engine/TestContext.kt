package com.mpx.player.playback.engine

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/**
 * Lightweight Context double for unit tests that provides temporary directories
 * for mpv config and cache operations without Android system dependencies.
 */
internal class TestContext(
    baseDir: File = File(System.getProperty("java.io.tmpdir"), "mpx-test-${System.nanoTime()}"),
) : ContextWrapper(null) {
    val files: File = File(baseDir, "files").apply { mkdirs() }
    val cache: File = File(baseDir, "cache").apply { mkdirs() }

    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = files
    override fun getCacheDir(): File = cache
}
