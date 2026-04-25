# Changelog

All notable changes to MPx Player should be documented in this file.

The format is inspired by Keep a Changelog, with entries grouped by release.

## [2.2.0

### Added

- Toggleable time display in player bottom controls (tap to switch between total duration and remaining time)
- Production-grade repository documentation set

### Changed

- Reworked top-level project documentation for users and contributors

### Fixed

- Fixed video lag during gestures by throttling notifyListeners() calls in seek, volume, and brightness drag operations (250ms for preview seek, 100ms for horizontal/vertical drags)

## [2.1.6] - 2026-04-08

This release focuses on making MPx Player feel more complete as a real daily-use app: cleaner UI, a built-in downloader workflow, PiP support, better search, better subtitle handling, and a long list of playback and release fixes.

### Added

- Built-in downloader workflow powered by `yt-dlp`
- Picture-in-Picture support
- Reels-style playback experience improvements
- Configurable double-tap seek duration
- Configurable horizontal drag seek sensitivity
- Editable title flow in downloader search
- Dynamic file size display in downloader quality selection

### Changed

- Improved overall UI polish across key app flows
- Improved search experience with fuzzier matching behavior
- Improved downloader usability and quality selection flow
- Improved right and left seek feedback behavior
- Updated screenshots and project presentation assets

### Fixed

- Fixed multiple subtitle detection issues
- Fixed audio track handling issues
- Fixed quality selector format issues in downloader
- Fixed release downloader bridge issues on Android builds
- Fixed white overlay issues during seek interactions
- Fixed several playback and UI regressions across the player
- Reduced Android release APK size significantly with arm64-focused packaging

### Changed

- Current app version from `pubspec.yaml`
