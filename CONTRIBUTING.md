# Contributing to MPx Player

Thanks for helping improve MPx Player.

## Ground rules

- Keep the app offline-first. Do not add analytics, tracking, ads, or hidden network calls.
- Follow the existing feature-based structure under `lib/features/`.
- Prefer small, focused pull requests.
- Match the app's current UI direction: bright mode should keep a blue, airy look.

## Local setup

1. Install Flutter and verify with `flutter --version`.
2. Clone the repo and open `mpx/`.
3. Run `flutter pub get`.
4. Run `flutter analyze` before opening a PR.
5. Test on a real Android device when touching playback, gestures, thumbnails, or storage access.

## Code expectations

- Use existing theme tokens from `lib/core/theme/app_theme_tokens.dart` instead of hardcoding light-mode colors.
- Prefer provider patterns already used in the codebase.
- Keep widgets lightweight; avoid unnecessary rebuilds in video and scrolling surfaces.
- Do not introduce destructive git changes in PRs.

## PR checklist

- Describe the user problem you fixed.
- Include screenshots or a short recording for UI changes.
- Mention tested devices or emulators.
- List verification steps.
- Note any known limitations.

## Publishing notes

Before a release, contributors should verify:

1. `flutter analyze` passes or only has known non-release warnings.
2. Playback works for local 720p and 1080p files.
3. Library scrolling feels smooth in list and grid modes.
4. Reels navigation is discoverable and exit behavior is clear.
5. App name, icon, version, and release notes are updated.

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
