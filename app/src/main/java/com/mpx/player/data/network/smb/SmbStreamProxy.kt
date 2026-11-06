package com.mpx.player.data.network.smb

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal localhost HTTP server that streams one registered file per token.
 *
 * Why a proxy instead of passing an smb:// URL to the player: Media3 has no
 * SMB DataSource, and embedding credentials in a URL would leak them into
 * playback history. The proxy URL (http://127.0.0.1:port/<token>) carries no
 * credentials, works with both engines, and supports HTTP Range requests so
 * seeking works. The opener runs on every request (initial load and seeks),
 * so each request opens a fresh stream at the requested offset.
 *
 * The server binds only to the loopback interface and serves only tokens
 * registered through [register]; unknown paths get 404.
 */
class SmbStreamProxy {

    /** An opened stream plus its total length, starting at [offset]. */
    class StreamHandle(
        val input: InputStream,
        val length: Long,
    )

    private val streams = ConcurrentHashMap<String, (Long) -> StreamHandle?>()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val baseUrl: String?
        get() = serverSocket?.let { "http://127.0.0.1:${it.localPort}" }

    /** Registers an opener and returns its localhost URL. Starts the server on first use. */
    @Synchronized
    fun register(open: (Long) -> StreamHandle?): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        streams[token] = open
        startLocked()
        return "${baseUrl}/$token"
    }

    fun unregister(url: String) {
        streams.remove(url.substringAfterLast("/"))
    }

    @Synchronized
    fun start() = startLocked()

    private fun startLocked() {
        if (running.get()) return
        val socket = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        serverSocket = socket
        running.set(true)
        acceptThread = Thread({
            while (running.get()) {
                try {
                    val client = socket.accept()
                    Thread({ handle(client) }, "smb-proxy-conn").start()
                } catch (_: Exception) {
                    // Socket closed by stop(); exit the loop.
                    break
                }
            }
        }, "smb-proxy-accept").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        streams.clear()
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1].trimStart('/')
            var rangeStart = 0L
            var rangeEnd: Long? = null
            // Consume headers.
            while (true) {
                val header = readLine(input) ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Range:", ignoreCase = true)) {
                    val value = header.substringAfter(":").trim()
                    if (value.startsWith("bytes=")) {
                        val range = value.removePrefix("bytes=").split("-")
                        rangeStart = range.getOrNull(0)?.toLongOrNull() ?: 0L
                        rangeEnd = range.getOrNull(1)?.toLongOrNull()
                    }
                }
            }
            val opener = streams[path]
            if (opener == null) {
                writeHeaders(output, 404, "Not Found", "text/plain", 0L, null)
                return
            }
            val handle = opener(rangeStart) ?: run {
                writeHeaders(output, 404, "Not Found", "text/plain", 0L, null)
                return
            }
            val total = handle.length
            val isRange = rangeStart > 0L || rangeEnd != null
            // Issue #253: an unsatisfiable range is 416 (RFC 7233), not
            // a 206 with a bogus length. The old arithmetic never even
            // got there: an out-of-range start made coerceIn throw
            // (min > max), so the connection was dropped with no
            // response at all. A range is unsatisfiable when the file
            // is empty, the start is past the end, or the end precedes
            // the start. The opened handle is closed on this path —
            // streamTo's finally only covers the success path.
            if (isRange && (total == 0L || rangeStart >= total || (rangeEnd != null && rangeEnd < rangeStart))) {
                writeHeaders(output, 416, "Range Not Satisfiable", "application/octet-stream", 0L, "bytes */$total")
                try {
                    handle.input.close()
                } catch (_: Exception) {
                }
                return
            }
            val start = rangeStart.coerceIn(0L, total)
            val end = (rangeEnd ?: (total - 1)).coerceIn(start, (total - 1).coerceAtLeast(0L))
            // An empty file with no Range is a plain 200 with length 0 —
            // the old (end - start + 1) computed 1 while 0 bytes follow.
            val length = if (total == 0L) 0L else (end - start + 1).coerceAtLeast(0L)
            if (isRange) {
                writeHeaders(output, 206, "Partial Content", "application/octet-stream", length, "bytes $start-$end/$total")
            } else {
                writeHeaders(output, 200, "OK", "application/octet-stream", length, null)
            }
            if (parts[0].equals("HEAD", ignoreCase = true)) return
            streamTo(handle.input, output, length)
        } catch (_: Exception) {
            // Client went away mid-stream; nothing to report.
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun streamTo(input: InputStream, output: OutputStream, length: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        try {
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                remaining -= read
            }
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeHeaders(
        output: OutputStream,
        code: Int,
        reason: String,
        contentType: String,
        contentLength: Long,
        contentRange: String?,
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code $reason\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: $contentLength\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
        sb.append("Connection: close\r\n")
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (prev == '\r'.code && sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }
}
