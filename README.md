# MPx Player

MPx Player is a fast, privacy-first Flutter video player built for people who want full control over local playback without ads, trackers, or cloud lock-in.

It combines an `mpv`-powered playback core with a polished mobile experience: gesture-driven seeking, subtitle controls, history, favorites, downloader tools, reels, and deep playback tuning.

![MPx Player screenshot](screenshots/1774286272927.jpg)

## Why It Stands Out

- Privacy first: no analytics, no tracking, no ad SDKs
- Playback focused: `mpv` engine, hardware acceleration, advanced seek and subtitle controls
- Built for real use: library indexing, favorites, watch history, downloader flows, PiP, and configurable gestures
- Contributor friendly: feature-based structure, clean separation of concerns, practical architecture

## Feature Highlights

### Playback

- `mpv`-backed video playback
- Double-tap seek with configurable seek duration
- Horizontal drag scrubbing
- Long press for temporary `2x` speed
- Brightness and volume swipe controls
- Aspect ratio controls and playback tuning
- Resume playback and watch history

### Subtitles and Audio

- External subtitle loading
- Subtitle size, color, weight, background, font, and position controls
- Audio track selection and restoration

### Library Experience

- Indexed local library for fast loading
- Folder browsing and search
- Favorites management
- Thumbnail support and metadata extraction

### Downloader and Reels

- Integrated downloader flow with `yt-dlp` via Chaquopy
- Share-target support
- Reels-style playback surface for short-form browsing

## Product Principles

MPx Player is shaped by a few non-negotiables:

- Local-first by default
- Fast interactions over flashy complexity
- User control over hidden automation
- Architecture that stays maintainable as features grow

## Screenshots

<p align="center">
  <img src="screenshots/1774286272927.jpg" width="180" alt="Library screen">
  <img src="screenshots/1774286272936.jpg" width="180" alt="Player screen">
  <img src="screenshots/1774286272944.jpg" width="180" alt="Playback controls">
  <img src="screenshots/1774286272958.jpg" width="180" alt="Settings screen">
</p>

## Architecture

The project follows a feature-first structure with clear boundaries between presentation, controller, domain, and data responsibilities.

```text
lib/
  core/
  features/
    player/
      controller/
      data/
      domain/
      presentation/
    downloader/
    library/
    reels/
    settings/
```

Useful references:

- `ARCHITECTURE.md`
- `CONTRIBUTING.md`

## Tech Stack

- Flutter
- Dart
- `flutter_mpv`, `flutter_mpv_video`, `flutter_mpv_libs_video`
- `provider`
- `sqflite`
- `shared_preferences`
- Chaquopy + `yt-dlp`

## Getting Started

### Prerequisites

- Flutter SDK
- Android Studio with Android SDK
- A physical Android device is strongly recommended for player work
- Python 3 for Chaquopy-based Android builds

### Setup

```bash
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player/mpx
flutter pub get
flutter analyze
flutter run
```

### Release Build

Current Android release packaging is optimized for `arm64-v8a`:

```bash
flutter build apk --release --target-platform android-arm64
```

## Contributing

MPx Player is actively shaped by improvements to performance, playback quality, UX polish, and architecture.

Contributions are especially valuable in these areas:

- playback reliability and performance
- subtitle and audio handling
- downloader robustness
- library UX and search
- tests, tooling, and architecture cleanup
- documentation and onboarding

Start here: `CONTRIBUTING.md`

## What Contributors Can Expect

- a codebase with real product scope
- meaningful performance and UX problems to solve
- room for both small fixes and deep systems work
- a project direction centered on user respect and technical quality

## License

This project is licensed under the MIT License. See `LICENSE`.

## Final Note

If you care about private, high-quality local media software, MPx Player is worth building with.

Whether you want to refine gestures, improve playback internals, redesign library flows, or harden release quality, your contribution can move the product forward in visible ways.
