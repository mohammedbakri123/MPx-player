# MPx

A Flutter media player. v1 scope: local library + search.

## Status

Fresh start (v3 development). Architecture decisions live in [ARCHITECTURE.md](ARCHITECTURE.md).

## Getting Started

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

## Stack

- Flutter 3.38 / Dart 3.10
- `provider` + `ChangeNotifier` for state
- sqflite (library index, forthcoming)
- Playback engine: TBD — hidden behind a repository interface

## Layout

```text
lib/
  app/        root app wiring
  core/       theme, db, platform services, utils
  features/   feature-first modules (library is active; player/reels/... reserved)
```
