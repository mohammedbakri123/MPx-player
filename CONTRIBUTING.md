# Contributing to MPx Player

Thanks for helping improve MPx Player.

## Ground rules

- Keep the app offline-first. Do not add analytics, tracking, ads, or hidden network calls.
- Follow the existing feature-based structure under `lib/features/`.
- Prefer small, focused pull requests with clearly stated user impact.
- Match the app's current UI direction: bright mode should keep a blue, airy look, and the UI should scale safely up to 1080p and beyond.
- Never hard-code engine parameters that might crash low-end devices without a fallback.

## Local setup

1. Install Flutter and verify with `flutter --version`.
2. Clone the repo and open `mpx/`.
3. Run `flutter pub get`.
4. Run `flutter analyze` before opening a PR. Check and fix any pre-existing warnings in files you edit.
5. **Always** test on a real Android device when touching playback, gestures, thumbnails, storage access, or the expert engine (`flutter_mpv`).

## Code expectations

- Use existing theme tokens from `lib/core/theme/app_theme_tokens.dart` instead of hardcoding colors.
- Prefer provider patterns already used in the codebase.
- Keep widgets lightweight; avoid unnecessary rebuilds in video surfaces, settings sheets, and scrolling thumbnail lists.
- Do not introduce destructive git changes in PRs.

## PR checklist

- Describe the user problem you fixed or the feature you added.
- Include screenshots or a short recording for UI changes (vital for Playback or Settings changes).
- Mention tested devices (physical devices heavily preferred over emulators).
- List verification steps so maintainers can reproduce your success.
- Note any known limitations (e.g. "Only works on H.264, software fallback triggered on HEVC").

## Publishing notes

Before making a release build, contributors should run through the following test matrix:

1. `flutter analyze` must pass or only have known acceptable non-release warnings.
2. Playback regression:
   - Play a local 720p file
   - Play a local 1080p file
   - Test rotation/fullscreen and system brightness/volume swipes
   - Test "Instant Seek" vs "Quality" engine profiles
3. Subtitle regression:
   - Ensure font type, weight, and size (up to 72pt) apply dynamically without crashing.
4. Library scrolling feels smooth in list and grid modes, and placeholders work if thumbnails timeout.
5. Reels navigation is discoverable and the exit hint is clear.
6. App name, icon, version (`pubspec.yaml`), and release notes are updated.

## Release workflow

### Android

1. Update version in `pubspec.yaml`.
2. Build with `flutter build apk --release` or `flutter build appbundle --release`.
3. Test the release build on a physical device.
4. Upload the signed artifact to Google Play.

### iOS

1. Update version in `pubspec.yaml` and Xcode if needed.
2. Build with `flutter build ios --release`.
3. Archive in Xcode.
4. Validate and upload through App Store Connect.

## Good first areas

- Playback polish
- Library performance
- Accessibility improvements
- Settings refinements
- Release readiness docs
