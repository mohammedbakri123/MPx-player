# MPx Player — native Android (Kotlin)

A native Android rewrite of MPx Player, replacing the Flutter app
(which is preserved, untouched, on the `feat/mpx-modern-ui`
branch of the Flutter repo).

## Stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- Playback engine: **not wired yet**. The player screen is a
  UI shell. The planned engine is Media3 (ExoPlayer) for
  streaming-style playback, with an FFmpeg-based path to be
  decided for local HW/SW decoding and ASS subtitles — the
  same two-layer shape MX uses (own FFmpeg engine for local,
  ExoPlayer for online). No playback is faked.
- Data: **not wired yet**. Library scanning (MediaStore),
  favourites, history, playlists, and downloads need a
  repository layer (Room planned). Screens show empty /
  placeholder states until then.

## Pages (MX structure, MPx's own code)

- Local — "Media" header, shortcut chips, Folders list
- Music — Tracks / Playlists / Albums / Artists / Folders tabs
- Transfer — downloader placeholder (disabled, not built)
- Me — tools grid, App Theme, Settings, Legal, Help
- Settings — plain list: List, Player, Decoder, Audio,
  Subtitle, General, Development, App Language
- Player — full-screen shell, black chrome, 16:9 video area

## Build (toolchain not installed on this VM yet)

Requires JDK 17, Android SDK (platform 34, build-tools),
and the Gradle wrapper. This VM lost its Java install in
a VM reset, so install the toolchain before building:

```sh
# install JDK 17 + Android command-line tools, set ANDROID_HOME,
# accept SDK licences, then open this project in Android Studio
# and sync / build from there.
# (No gradlew script is included in this skeleton yet.)
```

The project has not been compiled yet — that is the next
step once the toolchain is installed.
