# MPx Player

[![Latest release](https://img.shields.io/github/v/release/mohammedbakri123/MPx-player)](https://github.com/mohammedbakri123/MPx-player/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Stars](https://img.shields.io/github/stars/mohammedbakri123/MPx-player)](https://github.com/mohammedbakri123/MPx-player/stargazers)

A free, open-source media player for Android — built in native Kotlin with Jetpack Compose, powered by **mpv** for playback.

No ads. No tracking. Your files stay on your device.

## Download

**Latest: [v0.4.58](https://github.com/mohammedbakri123/MPx-player/releases/tag/v0.4.58)**

Download the `arm64` APK from [Releases](https://github.com/mohammedbakri123/MPx-player/releases/latest), then open it on your phone. Android will ask once to allow installs from your browser — that's normal for apps installed outside the Play Store.

- Requires Android 7.0+ (API 24)
- Current APK is ~27 MB (minified arm64)

> This is an active project under development. Releases are verified by green builds. If something breaks on your device, please report it — see [Community](#community) below.

## What it does

**Playback (mpv)**
- Local video and music, folders, playlists, history and favourites
- Wide codec support via mpv, including files other players refuse
- External subtitles — pick a `.srt` / `.ass` / `.ssa` / `.vtt` file while a video plays, and it stays selected
- Picture-in-Picture, double-tap to skip, resume where you left off

**Network**
- Play a video straight from a pasted URL (mpv plays http(s) directly)
- Browse and play from SMB shares

**Look and feel**
- True-black dark theme + light theme
- Translated into 9 languages (English, Arabic, German, Spanish, French, Hindi, Portuguese, Russian, Chinese)

## Screenshots

Real phone screenshots are coming — the ones in this repo's history were early desktop mockups, not the current app.

If you try v0.4.58, a screenshot of the player or Library in [Discussions](https://github.com/mohammedbakri123/MPx-player/discussions) would help a lot.

## Community

This is where the project lives — please use these, not DMs:

- **Try it & give feedback:** [GitHub Discussions](https://github.com/mohammedbakri123/MPx-player/discussions) — tell us what works, what's slow, what's confusing. Device + Android version + what you were playing helps more than "it doesn't work".
- **Bugs & feature requests:** [Issues](https://github.com/mohammedbakri123/MPx-player/issues) — use the templates. Search first; a duplicate with a "+1" and your device info is still useful.
- **Chat with other users:** [Telegram group](https://t.me/+lFc18fX5Im40MGE0)
- **Contribute code:** see [CONTRIBUTING.md](CONTRIBUTING.md). Small fixes are welcome — check the [`good first issue`](https://github.com/mohammedbakri123/MPx-player/labels/good%20first%20issue) label.

Please be kind. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Build from source

Requires JDK 17 and the Android SDK (platform 36, build-tools).

```sh
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player
./gradlew --no-daemon assembleDebug
```

Notes from building on this project:

- Gradle runs as root on some machines, so `mavenLocal()` resolves to `/root/.m2/repository`, not `~/.m2/repository`. Builds that need the mpv AAR must pass `-Dmaven.repo.local=$HOME/.m2/repository` (or set it in `gradle.properties`).
- A fresh Gradle home cannot always fetch the mpv AAR — reuse a Gradle home that already has it cached.
- Prefer `--no-daemon` builds; the Gradle daemon is blocked on some VMs.
- The debug build does **not** run R8. Before a release, always build the minified release variant too and check its output — a green debug build is no proof the release build works (v0.3.5 crashed at launch for exactly this reason: R8 stripped a library the downloader loads by reflection).

Full contributor guide: [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

- Smoother scrolling on Library / Music
- MX-style rotation: videos stay portrait until you rotate
- Better landscape player controls
- More theme / accent colours
- F-Droid listing (after the current fix round lands)

See [open issues](https://github.com/mohammedbakri123/MPx-player/issues) for what's being worked on now. The current focus is fixing what's there before adding new features.

## Acknowledgements

- [mpv](https://mpv.io/) and [mpv-android](https://github.com/mpv-android/mpv-android) — the playback engine, under its licence
- [Seal](https://github.com/JunkFood02/Seal) — the in-app self-update checker and the shared-link URL matcher are adapted from Seal's `UpdateUtil` and `matchUrlFromSharedText`, with attribution in the code. The Seal-style downloader itself was removed in 2026-11.
- MX Player — UI inspiration only. MPx copies layout ideas in its own code; it does not use MX's source, binaries, assets, strings, or branding, and never will.

## License

MPx Player is free software under the **GNU General Public License v3.0** — see [LICENSE](LICENSE). That means anyone can use, study, share, and improve it, as long as derived work stays under the same licence.
