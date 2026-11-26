# Changelog

All notable changes to MPx Player will be documented in this file.

## Unreleased

## v0.4.67

- Added Android TV support: Leanback launcher entry, `touchscreen` `required="false"`, and 320×180dp TV banner
- Added TV device detection (`TvUtils`, `LocalIsTv`), `MpxNavigationRail` start-edge navigation on TV, and crisp D-pad focus highlights (`mpxFocusHighlight`) across library, network, playback sheet, dialogs, and settings screens
- Fixed TV player orientation so playback stays landscape with TV transport chrome, and added full D-pad & hardware media-key player controls (`PlayerKeyHandler`)

## v0.4.66

- Fixed progress bar slider dragging to seek — `FloatingIsland` now only claims unconsumed `DOWN` pointers so `Slider` pre-slop `MOVE` events are never stolen before drag starts (#499)
- Added YouTube / MX Player style seek preview thumbnails when scrubbing the progress bar (floating card above the slider thumb) and during horizontal swipe-seek gestures (#500)

## v0.4.65

- Player seek gestures: fixed unpredictable position jumps when scrubbing mid-video — gesture detector was reading stale state captured at `currentDuration` change instead of live position, causing seeks to snap back to 00:00 or resume offset
- Accidental horizontal seeks from palm grips, system back-swipes, and vertical brightness/volume drags — now require a 24dp outer edge exclusion zone and 1.5× horizontal dominance before locking seek mode
- Double-tap seek misfires on wrong zones (tapping centre then right now correctly toggles play/pause then skips, not double-skip), and cross-zone taps no longer trigger seek
- Cancelled and system-interrupted drags now properly release pause state via `finally` cleanup
- Missed button/slider taps on floating chrome pills no longer fall through to the video gesture detector
- Rapid consecutive double-taps chain seek targets correctly (+10s, +20s, … rather than repeating from the same base position)

## v0.4.62

- Decoder pill is now a small island with only the mode letters (HW / HW+ / SW), and the video title is back
- Windows-1256 Arabic subtitles now render correctly (transcoded to UTF-8)
- Strip menu aligns to the left edge in landscape
- Media Information from the file list: long-press a video, tap the Info icon
- Me page: Tools section removed, new Collection section (History, Favourites, Playlists)
