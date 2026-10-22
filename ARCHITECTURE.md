# MPx Architecture

## Scope

v1 targets one feature: **local library + search**. Playback is intentionally out of scope until the engine decision is made — but the architecture reserves a seam for it (see Player Seam).

## Top-Level Shape

```text
lib/
  app/                  root widget, MaterialApp wiring, future router + DI setup
  core/
    db/                 sqflite database + per-entity operation classes (added with library index)
    theme/              ThemeData + design tokens
    ui/                 shared widgets (empty/error states, bottom-sheet chrome) — grows as needed
    utils/              logging, time formatting, debounce helpers
  features/
    library/            the v1 feature
    player/             reserved (planned: controller | data | domain | presentation)
    reels/              reserved
    favorites/          reserved
    history/            reserved
    settings/           reserved
    downloader/         reserved (Dart only; platform bridge behind a service interface)
```

Folders are created when their feature starts. Do not pre-create empty reserved dirs.

## Layering Model

Each feature:

```text
features/<feature>/
  controller/     ChangeNotifier controllers: user actions, view state, transitions
  data/           datasources (storage/platform) + repository implementations
  domain/         entities + repository interfaces (Flutter-free)
  services/       cross-cutting feature services (indexing, thumbnails, settings persistence)
  presentation/   screens + widgets, render only
```

Dependency direction: `presentation -> controller -> domain <- data`. Controllers see interfaces; presentation sees controllers only. UI never touches datasources directly.

## State Management

`provider` + `ChangeNotifier` (Decision: keep what worked last time, with discipline).

Rules — non-negotiable after the v2.x codebase findings:

1. Every `StreamSubscription` obtained by a controller is stored in a field and cancelled in `dispose()`. No anonymous `.listen` without a field.
2. Any async gap between an event and `notifyListeners()` must be guarded (`if (disposed) return;` — controllers expose a private `_disposed` flag set first in `dispose()`). `try { notifyListeners(); } catch (_) {}` is forbidden at call sites; guard at source instead.
3. `setState` after `await` requires `mounted` check. `use_build_context_synchronously` is on and not suppressed per-file; fix the call site instead.
4. Rebuild scopes are narrow: prefer `Selector<Class, Slice>(...)` over whole-controller `AnimatedBuilder`/`Consumer` — especially for sheets/dialogs that only need a config slice.
5. Persistence writes are debounced/throttled — slider drags and per-event writes are not allowed to hit SharedPreferences/db directly.
6. Services never expose `static late` fields. Accessors return safely-initialized values or throw with a clear message at construction time, not deep in a build method.

## Player Seam

Playback engine is undecided (`flutter_mpv` vs `media_kit` vs `video_player`). Regardless of choice:

- `features/player/domain/repositories/player_repository.dart` is the only surface the player controller consumes.
- Engine-specific code lives in `features/player/data/repositories/<engine>_player_repository.dart`.
- `flutter_mpv`-family APIs that leak engine types (e.g. `VideoController`) must be behind the repository, never used by controllers/presentation directly.
- This keeps the engine decision reversible.

## Database

v1 will re-use the proven v2 shape when the index lands: one `AppDatabase` (sqflite) + thin per-entity operation classes (`library_index_operations.dart`, etc.) in `core/db/operations/`. Repositories in `data/` call operations only.

Thumbnail disk cache keys must be stable across runs/platforms (path digest, not `String.hashCode`).

## Bootstrap Order

`main()` does not await services. The splash screen owns initialization so the first frame paints immediately. Legacy pattern to avoid: `await X.init(); runApp()` in main (v2.x did this and stared at a cold native splash).

## Error Handling

- `AppLogger` (core/utils) is the single logging entry; `print`/`debugPrint` outside of it are lint-blocked by convention.
- Catch blocks log or `rethrow` — empty catch bodies are errors under review.
- `catch (e, s)` is preferred where context loss would hurt debugging.

## Testing Expectations

- Controllers and services are unit-tested at birth, not retrofitted. pubspec already ships `mockito` + `build_runner`.
- At minimum: one test file per controller/service created in v1.
- Prefer testing against the domain interfaces (fakes), not engine concretes — that is the point of the Player Seam.

## Practical Rules for Contributors

- New features extend the existing shape; do not invent parallel structures per folder.
- Platform specifics (storage paths, permissions, engines) stay in `data/` or `core/services` — never in `presentation/`.
- If a change is hard to explain in two paragraphs, the design wants simplification first.
