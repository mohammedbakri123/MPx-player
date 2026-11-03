package com.mpx.player.ui

/**
 * Forwards an activity-result callback from a launcher owned above the NavHost
 * (in MpxApp, where an ActivityResultRegistryOwner exists) to the destination
 * screen that owns the result logic.
 *
 * NavHost destinations in navigation-compose 2.7.x do NOT receive a
 * LocalActivityResultRegistryOwner, so calling rememberLauncherForActivityResult
 * inside a destination crashes on first composition with
 * "No ActivityResultRegistryOwner was provided". The launcher must live above
 * the NavHost; the destination only supplies (and clears) the callback.
 */
class ActivityResultHandler<T> {
    var callback: ((T) -> Unit)? = null

    fun invoke(value: T) {
        callback?.invoke(value)
    }
}
