package com.mpx.player.data.network.smb

/**
 * Application-scoped owner of the live SMB playback session (issue #229).
 *
 * The SMB browser dialog creates one [SmbStreamProxy] and one
 * [SmbConnection] per dialog opening, but both must outlive the dialog:
 * playback (and seeks, which re-request ranges through the proxy) goes on
 * after the dialog is dismissed. Before this holder existed nothing ever
 * closed them — every SMB video played leaked an open SMB session and a
 * bound loopback server socket until the process died, and opening the
 * dialog again created a second pair.
 *
 * Lifetime: the dialog hands its proxy + connection over via [start] when
 * the user taps play. [PlaybackManager] closes the session via [close]
 * when no SMB item remains in the queue (a fresh non-SMB queue in
 * playQueue) and when playback is deliberately stopped (stop()/release()).
 * Pause never closes it — a paused SMB video still needs its connection
 * for resume/seek, and replay-after-EOF needs the proxy too. Starting a
 * new SMB play closes the previous session first: only one SMB item can
 * be in the queue at a time (each SMB play replaces the queue), so the
 * old session is unreachable by then.
 *
 * Credentials are held only in memory inside [SmbConnection] for the life
 * of the session and are never logged or persisted.
 */
object SmbPlaybackSession {

    /** Prefix of the queue id of an SMB item: "smb:<host>/<share>/<path>". */
    const val SMB_ID_PREFIX = "smb:"

    fun isSmbId(id: String): Boolean = id.startsWith(SMB_ID_PREFIX)

    private var proxy: SmbStreamProxy? = null
    private var connection: SmbConnection? = null

    /**
     * Takes ownership of a live proxy + connection pair, closing any
     * previous session first. Called by the SMB dialog when playback starts.
     */
    @Synchronized
    fun start(proxy: SmbStreamProxy, connection: SmbConnection) {
        closeLocked()
        this.proxy = proxy
        this.connection = connection
    }

    /**
     * Stops the proxy server and closes the SMB connection, if any.
     * Idempotent; safe to call when no session is active.
     */
    @Synchronized
    fun close() {
        closeLocked()
    }

    private fun closeLocked() {
        try {
            proxy?.stop()
        } catch (_: Exception) {
        }
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        proxy = null
        connection = null
    }
}
