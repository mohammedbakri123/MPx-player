package com.mpx.player.data.network.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** One browsable entry returned by [SmbConnection.listDirectory]. */
data class SmbEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

/**
 * Thin wrapper over smbj (pure-Java SMB2/3 client, Apache 2.0).
 *
 * One instance = one authenticated session to one host. All calls block and
 * must run off the main thread (callers use Dispatchers.IO). Credentials are
 * held only in memory for the life of this instance and are never logged or
 * persisted; callers persist only the host (see AppSettings.lastSmbHost).
 *
 * Share listing: smbj 0.12.0 exposes NO share-enumeration API (no
 * Session/Connection listShares), so [listShares] returns an empty list and
 * callers fall back to asking the user for a share name (the dialog's manual
 * share field). Do not reintroduce a fake listShares call.
 */
class SmbConnection(
    private val host: String,
    private val username: String,
    private val password: String,
    private val domain: String,
) {
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private val lock = Any()

    /** Connects and authenticates. Empty username = guest/anonymous attempt. */
    fun connect() {
        synchronized(lock) {
            val config = com.hierynomus.smbj.SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build()
            val smbClient = SMBClient(config)
            client = smbClient
            val conn = smbClient.connect(host)
            connection = conn
            val auth = if (username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(username, password.toCharArray(), domain)
            }
            session = conn.authenticate(auth)
        }
    }

    /** Disk share names on this host. smbj 0.12.0 has no enumeration API,
     * so this always returns empty and the UI asks for a share name. */
    fun listShares(): List<String> {
        if (session == null) throw IOException("Not connected")
        return emptyList()
    }

    /**
     * Lists one directory. [share] is a share name, [path] is "" for the
     * share root or slash-separated subfolders. "." and ".." are filtered.
     */
    fun listDirectory(share: String, path: String): List<SmbEntry> {
        val disk = openShare(share)
        try {
            return disk.list(path, "*")
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    SmbEntry(
                        name = info.fileName,
                        isDirectory = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L,
                        size = info.endOfFile,
                    )
                }
        } finally {
            disk.close()
        }
    }

    /** Opens a file stream starting at [offset]; total length comes from the share. */
    fun openStream(share: String, path: String, offset: Long): SmbStreamProxy.StreamHandle? {
        val disk = openShare(share)
        val file = disk.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            emptySet(),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            emptySet<SMB2CreateOptions>(),
        )
        val length = file.fileInformation.standardInformation.endOfFile
        val stream = SmbFileInputStream(disk, file, offset)
        return SmbStreamProxy.StreamHandle(stream, length)
    }

    private fun openShare(share: String): DiskShare {
        val sess = session ?: throw IOException("Not connected")
        val opened = sess.connectShare(share)
        return opened as? DiskShare ?: throw IOException("Share is not a disk share: $share")
    }

    fun close() {
        synchronized(lock) {
            try {
                connection?.close()
            } catch (_: Exception) {
            }
            try {
                client?.close()
            } catch (_: Exception) {
            }
            connection = null
            session = null
            client = null
        }
    }
}

/**
 * InputStream over one open smbj file, reading sequentially from [startOffset].
 * Closing the stream closes the file and its share handle.
 */
private class SmbFileInputStream(
    private val share: DiskShare,
    private val file: com.hierynomus.smbj.share.File,
    startOffset: Long,
) : InputStream() {
    private var position = startOffset
    private var closed = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IOException("Stream closed")
        if (length == 0) return 0
        val read = file.read(buffer, position, offset, length)
        if (read <= 0) return -1
        position += read
        return read
    }

    /**
     * Issue #301: random-access skip — advancing the read position
     * transfers nothing over SMB. The default InputStream.skip would
     * read-and-discard the skipped bytes; for a suffix range near the
     * end of a large file that would download almost the whole file
     * first. Reads past the end still return -1, as before.
     */
    override fun skip(n: Long): Long {
        if (closed) throw IOException("Stream closed")
        if (n <= 0L) return 0L
        position += n
        return n
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            file.close()
        } catch (_: Exception) {
        }
        try {
            share.close()
        } catch (_: Exception) {
        }
    }
}
