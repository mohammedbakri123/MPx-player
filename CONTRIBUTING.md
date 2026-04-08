# Contributing to MPx Player

Thanks for considering a contribution.

MPx Player is not a demo app. It is a real product codebase focused on private, high-quality local media playback. That means contributions should improve the app in ways users can feel: smoother playback, clearer UI, stronger architecture, fewer regressions, and better reliability.

## What We Value

- Privacy: no analytics, no trackers, no ad tech
- Performance: playback and gestures should feel immediate
- Maintainability: features should fit the existing structure cleanly
- Product quality: polish matters, not just functionality

## Good First Contributions

- fix playback or gesture regressions
- improve subtitle, audio track, or history behavior
- strengthen downloader error handling
- add or improve tests
- refine docs and developer onboarding
- improve search, indexing, or library UX

## Development Setup

### Requirements

- Flutter SDK
- Android Studio and Android SDK
- Python 3 for Android builds using Chaquopy
- A physical Android device for player-related testing when possible

### Local Setup

```bash
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player/mpx
flutter pub get
flutter analyze
flutter test
flutter run
```

## Project Shape

The codebase is organized by feature, with internal separation between presentation, controller, domain, and data layers.

Typical feature layout:

```text
lib/features/<feature>/
  controller/
  data/
  domain/
  presentation/
```

Use this structure when adding new work unless there is a strong reason not to.

## Working Principles

### 1. Respect Layer Boundaries

- presentation code should stay UI-focused
- business rules belong in controllers/domain logic
- persistence and platform details belong in data/services

### 2. Preserve User Trust

- do not add telemetry
- do not add hidden network behavior
- do not weaken offline-first behavior without a strong product reason

### 3. Optimize for Responsiveness

- avoid unnecessary rebuilds in large widget trees
- be careful with overlays, gestures, and animated layers on the player surface
- test interaction-heavy changes on a real device when possible

### 4. Match Existing Style

- use `snake_case` for files
- use `PascalCase` for classes
- use `camelCase` for fields and methods
- prefer small, focused widgets and services

## Before You Open a PR

Please make sure your change does the following:

- solves one clear problem well
- keeps code readable
- avoids unrelated refactors unless necessary
- passes analysis and tests
- includes manual verification notes for UI or playback changes

Recommended checks:

```bash
flutter analyze
flutter test
```

If you changed Android release behavior, also verify a release build:

```bash
flutter build apk --release --target-platform android-arm64
```

## Pull Request Guidance

When opening a PR, include:

- what changed
- why it changed
- screenshots or screen recordings for UI work
- device and build mode used for testing when relevant
- any known tradeoffs or follow-up work

Small, focused PRs are much easier to review and merge than broad mixed changes.

## Areas Where Contributors Can Have Big Impact

- player performance and gesture quality
- subtitle system improvements
- downloader resilience and UX
- release engineering and APK size optimization
- architecture cleanup and test coverage
- documentation that helps users and new contributors succeed

## If You Are Unsure Where to Start

Open an issue, propose a focused improvement, or start with a bug fix in one of the high-impact areas above.

Strong contributions are not only large features. A well-executed fix for a frustrating playback bug can be just as valuable.

## Thank You

If you choose to spend time on MPx Player, you are helping build a more respectful kind of media app: fast, private, useful, and technically serious.
