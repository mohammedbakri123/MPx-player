# 🤝 Contributing to MPx Player

Welcome! We are incredibly excited that you are interested in contributing to MPx Player. This guide provides an exhaustive walkthrough for setting up your environment, understanding our code standards, and successfully merging your code.

---

## 🛡️ 1. Core Development Tenets

Before writing a single line of code, understand these non-negotiable rules:
1. **Privacy Absolute:** NO telemetry, NO analytics, NO crash reporting frameworks (like Firebase or Sentry), NO network calls. The app must function entirely isolated from the internet.
2. **Clean Architecture Strictness:** UI must never import anything from `dart:io`, `sqflite`, or `media_kit`. Business logic must never import `package:flutter/material.dart`.
3. **Performance First:** The UI must maintain 60/120fps. Avoid `context.watch()` at the top of large widget trees.

---

## 🛠️ 2. Comprehensive Environment Setup

### System Prerequisites
- **Flutter SDK:** `>= 3.0.0`
- **Dart SDK:** `>= 3.0.0`
- **Android Development:** Android Studio, Android SDK (API 34+), and **NDK (Side-by-side)** installed (crucial for compiling the C++ backing of `flutter_mpv`).
- **iOS Development:** macOS, Xcode 14+, CocoaPods.

### Step-by-Step Initialization
1. **Fork & Clone:**
   ```bash
   git clone https://github.com/your-username/mpx-player.git
   cd mpx-player/mpx
   ```

2. **Fetch Dependencies:**
   ```bash
   flutter pub get
   ```

3. **Verify Static Analysis:**
   Ensure your local environment passes our strict linting rules.
   ```bash
   flutter analyze
   ```

4. **Run Unit Tests:**
   ```bash
   flutter test
   ```

5. **Deploy to Device:**
   > ⚠️ **CRITICAL:** Emulators do not support hardware video decoding accurately. You **must** test on a physical Android or iOS device when working on player logic.
   ```bash
   flutter run -d <device_id> --profile
   ```

---

## 🏗️ 3. How to Add a New Feature

Adding a feature requires adhering to our Feature-First Clean Architecture. Let's say you are adding a "Playlists" feature.

1. **Create the Directory:** `lib/features/playlists/`
2. **Domain Layer:** 
   - Create `domain/entities/playlist.dart` (Pure Dart class).
   - Create `domain/repositories/playlist_repository.dart` (Abstract class).
3. **Data Layer:**
   - Create `data/repositories/playlist_repository_impl.dart` (Implements the interface, talks to SQLite).
4. **Controller Layer:**
   - Create `controller/playlist_controller.dart` (Extends `ChangeNotifier`, uses the repository interface).
5. **Presentation Layer:**
   - Create `presentation/screens/playlist_screen.dart`.
   - Access the controller using `context.watch<PlaylistController>()`.
6. **Dependency Injection:**
   - Register your new controller in `main.dart` inside the `MultiProvider`.

---

## 🎨 4. Code Style & Best Practices

### Formatting & Linting
We strictly enforce standard Dart formatting.
```bash
# Auto-format your code before committing
dart format .
```

### State Management (`provider`) Anti-Patterns to Avoid
- 🚫 **BAD:** Using `context.watch<Controller>()` inside an `onPressed` callback. (Will throw an exception).
- ✅ **GOOD:** Using `context.read<Controller>()` inside an `onPressed` callback.
- 🚫 **BAD:** Wrapping an entire `Scaffold` in a `Consumer` just to update a single text string.
- ✅ **GOOD:** Wrapping only the `Text` widget in a `Consumer` or `Selector`.

### Naming Conventions
- Variables/Methods: `camelCase`
- Classes/Enums: `PascalCase`
- Files/Folders: `snake_case` (e.g., `video_player_screen.dart`)
- Private variables must be prefixed with an underscore `_`.

### Documentation
Use `///` for public APIs, classes, and complex methods.
```dart
/// Parses the raw directory path and filters out hidden files.
/// Throws a [FileSystemException] if the path does not exist.
List<FileItem> parseDirectory(String path) { ... }
```

---

## 🌿 5. Git Workflow & Branching Strategy

We use a standard Feature Branch workflow and **Conventional Commits**.

### Branch Naming
- Features: `feature/brief-description`
- Bug Fixes: `fix/issue-description`
- Refactors: `refactor/what-is-changed`

### Conventional Commits
Your commit messages must follow this format to allow automated changelog generation:
`<type>(<scope>): <description>`

- `feat:` A new feature (e.g., `feat(player): add subtitle offset control`)
- `fix:` A bug fix (e.g., `fix(library): resolve crash on empty folders`)
- `refactor:` Code restructuring (e.g., `refactor(db): migrate sqflite helper to singleton`)
- `perf:` Performance improvements
- `style:` Formatting, missing semi-colons, etc.

---

## 🧪 6. Comprehensive Testing Guide

We require tests for all business logic (Controllers) and data manipulation (Services/Repositories).

1. **Unit Tests (`test/features/.../controller_test.dart`)**
   Use `mocktail` or `mockito` to mock Repositories.
   ```dart
   test('Should update state to playing when play is called', () async {
     when(() => mockRepo.play()).thenAnswer((_) async => Future.value());
     await controller.play();
     expect(controller.isPlaying, true);
   });
   ```

2. **Widget Tests**
   Ensure UI components render correctly given a specific mocked state.

**To run all tests:**
```bash
flutter test --coverage
```

---

## 🚀 7. Pull Request & Review Process

1. **Push your branch** to your fork.
2. **Open a Pull Request** against the `main` branch.
3. **Fill out the PR Template** thoroughly. Include screen recordings if you modified UI/UX.
4. **CI Checks:** Ensure GitHub Actions (Linting, Tests) pass green.
5. **Code Review:** A core maintainer will review your code. Address any requested changes.
6. **Merge:** Once approved, a maintainer will squash and merge your PR.

---

## 📦 8. Release Management (Maintainers Only)

To publish a new version:
1. Update `version` in `pubspec.yaml` (e.g., `1.2.0+14`).
2. Update `CHANGELOG.md`.
3. Build release artifacts:
   ```bash
   flutter build apk --release --split-per-abi
   flutter build appbundle --release
   flutter build ios --release
   ```
4. Create a GitHub Release with the APKs attached.

---
Thank you for making MPx Player better! 🎬