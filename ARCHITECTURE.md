# MPx Player Architecture

This document explains how MPx Player is organized, why the codebase is structured the way it is, and how new work should fit into the system.

It is written for contributors and maintainers who need a practical mental model of the app, not just a list of folders.

## Architectural Intent

MPx Player is built around four goals:

- keep playback interactions responsive
- keep features isolated enough to evolve safely
- keep platform-specific details away from UI code
- keep the app maintainable as product scope grows

The result is a feature-first Flutter application with clean separation between presentation, controller, domain, and data responsibilities.

## Top-Level Shape

```text
lib/
  core/
  features/
    downloader/
    library/
    player/
    reels/
    settings/
```

## Layering Model

Most features follow this shape:

```text
lib/features/<feature>/
  controller/
  data/
  domain/
  presentation/
```

### Presentation

The presentation layer contains screens and widgets.

Responsibilities:

- render state
- dispatch user actions
- coordinate visible UI behavior

Avoid placing business rules or persistence logic here.

### Controller

Controllers coordinate feature behavior and hold mutable UI-facing state.

Responsibilities:

- respond to user actions
- call repositories and services
- manage view state and transitions
- notify the UI efficiently

MPx Player commonly uses `ChangeNotifier`-based controllers and selective rebuild patterns to keep interactions fast.

### Domain

The domain layer defines feature concepts and contracts.

Responsibilities:

- entities
- repository interfaces
- feature-level abstractions

This layer should stay as independent as practical from Flutter-specific concerns.

### Data

The data layer implements domain contracts and talks to concrete systems.

Responsibilities:

- local storage
- platform channels
- media engines
- downloader plumbing
- repository implementations

## Core Systems

### Player Stack

The player is one of the most interaction-sensitive areas of the codebase.

Key characteristics:

- built on `flutter_mpv` and related packages
- gesture-heavy UI with multiple overlay layers
- subtitle and audio controls
- watch history and resume behavior
- runtime state driven by controller updates and engine-backed data

Because the player surface is sensitive to rebuild cost and overlay composition, changes here should be tested carefully in both debug and release modes.

### Library Stack

The library side focuses on quick discovery and predictable local browsing.

Key characteristics:

- indexed local media experience
- search and folder browsing
- metadata extraction and thumbnails
- favorites and history integration

### Downloader Stack

The downloader combines Dart, Kotlin, Python, and Android packaging concerns.

Key characteristics:

- Flutter UI and controller flow in Dart
- Android bridge code in Kotlin
- `yt-dlp` runtime logic through Chaquopy Python integration
- release-build sensitivity because obfuscation and packaging can break bridge calls

Any downloader change should be considered across all four layers: Dart, platform bridge, Python runtime, and release build behavior.

## State Management Approach

MPx Player uses `provider` with `ChangeNotifier` patterns.

Guidelines:

- keep rebuild scopes narrow
- prefer focused selectors and targeted listeners
- avoid watching broad controller state high in large trees
- treat the player surface as performance-sensitive UI

## Performance Priorities

This project is especially sensitive in these areas:

- video surface overlays
- gesture handling
- subtitle rendering
- library loading and search
- downloader release behavior

When touching those systems, prefer:

- smaller rebuild scopes
- explicit verification on real devices
- release-build checks for Android-specific flows

## Practical Rules for Contributors

### Add New Features by Extending Existing Patterns

New work should generally:

- live inside a feature folder
- define domain contracts before concrete implementations when appropriate
- keep UI logic separate from persistence and platform behavior

### Keep Platform Complexity Contained

Android-specific build, bridge, or runtime details should stay out of general UI code unless there is no cleaner boundary.

### Preserve Product Direction

Architecture decisions should support the product principles in `README.md`:

- privacy first
- fast local playback
- user control
- maintainable growth

## Important Documentation Links

- `README.md`
- `CONTRIBUTING.md`
- `SECURITY.md`
- `CHANGELOG.md`

## Final Guidance

If you are changing a system and it feels hard to explain in one or two paragraphs, that is often a sign that the design needs simplification.

Good architecture in MPx Player should make feature work easier, not just look clean on paper.
