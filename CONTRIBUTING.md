# Contributing to MPx Player

Thanks for being interested in MPx Player.

This project is built like a real product, not a throwaway sample. That means contributions should improve the app in ways users can actually feel: faster playback, fewer regressions, better interaction quality, cleaner architecture, and clearer documentation.

## What This Project Optimizes For

- Privacy: no analytics, no trackers, no ad tech
- Quality: features should feel intentional and complete
- Performance: playback and gestures should stay responsive
- Maintainability: code should fit the structure and remain understandable

## Where Contributions Matter Most

- player performance and gesture handling
- subtitle and audio track behavior
- downloader reliability and UX
- library indexing and search
- testing and regression protection
- release engineering and APK size work
- documentation and onboarding

## Development Setup

### Requirements

- Flutter SDK
- Android Studio and Android SDK
- Python 3 for Android builds that use Chaquopy
- A physical Android device when testing player interactions

### Local Setup

```bash
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player/mpx
flutter pub get
flutter analyze
flutter test
flutter run
```

### Release Verification

If your change affects Android release behavior, verify the release build too:

```bash
flutter build apk --release --target-platform android-arm64
```

## Codebase Shape

The project is feature-first.

```text
lib/features/<feature>/
  controller/
  data/
  domain/
  presentation/
```

Follow the existing structure unless there is a strong architectural reason not to.

## Working Rules

### Respect Boundaries

- keep presentation code focused on UI
- keep business behavior in controllers and domain logic
- keep platform, storage, and service details in data or core services

### Protect User Trust

- do not add telemetry
- do not add hidden network behavior
- do not introduce dark patterns or intrusive flows

### Protect Performance

- be careful with overlays and gesture layers on top of video surfaces
- avoid unnecessary rebuilds in large trees
- test interaction-heavy changes on real hardware when possible

### Keep Changes Focused

- solve one clear problem per PR when possible
- avoid mixing broad refactors with bug fixes unless necessary
- leave the code easier to understand than you found it

## Before Opening a PR

Please make sure your change:

- addresses a real problem clearly
- matches existing naming and structure
- passes `flutter analyze`
- passes relevant tests
- includes manual verification notes for playback or UI work

Recommended checks:

```bash
flutter analyze
flutter test
```

## Pull Request Expectations

When you open a PR, include:

- what changed
- why it changed
- screenshots or recordings for UI changes
- device and build mode used for testing when relevant
- any known follow-up work or tradeoffs

Small, focused pull requests are much easier to review and merge.

## Good First Contributions

- fix a reproducible playback or gesture bug
- improve downloader error messages or handling
- add tests around controller logic
- improve docs for setup, release, or architecture
- polish a feature without changing the project direction

## Communication

If you are unsure where to start, open an issue or propose a focused improvement before investing in a large change.

Well-executed bug fixes, testing improvements, and docs work are absolutely valuable contributions.

## License Reminder

By contributing to this repository, you agree that your contributions will be licensed under the same MIT License that covers the project. See `LICENSE`.

## Thank You

Every solid contribution helps move MPx Player toward a better kind of media app: fast, private, useful, and technically serious.
