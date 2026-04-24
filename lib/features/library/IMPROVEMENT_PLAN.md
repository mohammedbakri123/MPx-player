# Library Feature Improvement Plan

## Overview

This document outlines the plan to improve the `lib/features/library` module in three key areas:
1. **Performance & Speed** – reduce jank, optimize file I/O, and improve perceived performance
2. **View Modes** – currently only list/grid exist (and reset); we need proper tree/list/grid views
3. **Persistence** – view mode, sort order, and filter settings must survive app restarts

---

## 1. Current Issues Identified

### 1.1 No Persistence
- `_isGridView`, `_sortBy`, `_sortOrder`, and `_showOnlyVideos` are **hard-coded defaults** in `FileBrowserController`
- Every time `HomeScreen` is opened, a brand-new `FileBrowserController()` is created → all UI state resets
- Only navigation path history is "persisted" via static variables (RAM-only, lost on app kill)

### 1.2 Performance Bottlenecks
| Issue | Location | Impact |
|---|---|---|
| Synchronous sorting on main thread | `_sortItems()` | Jank on folders with 200+ items |
| Recursive FS scan for empty-folder hiding | `_countVideosRecursively()` | Blocks UI during hydration |
| No isolate usage for heavy I/O | `DirectoryBrowser`, controller | File operations stall frames |
| Multiple `notifyListeners()` per load | `loadDirectory()`, hydration | Causes 3-4 full rebuilds per navigation |
| Thumbnails lack `RepaintBoundary` | `LazyThumbnail`, `_GridItem` | Unnecessary repaints on scroll |
| Controller recreated per visit | `HomeScreen.initState()` | Loses cache, re-indexes, re-thumbnails |
| Grid skeleton & grid layout re-calculated | `FileBrowserContent._buildGridView()` | Layout math every rebuild |
| Search does linear scan + fuzzy matching | `LibraryIndexService.search()` | O(n) per query, no FTS |

### 1.3 View-Mode Gaps
- Current code has a boolean `_isGridView` (list vs grid)
- User explicitly wants **tree view** (expandable hierarchy) in addition to list & grid
- There is no UI to pick between three modes—only a binary toggle icon

---

## 2. Proposed Architecture Changes

### 2.1 New Files to Create

```
lib/features/library/
├── services/
│   └── library_preferences_service.dart   # NEW: SharedPreferences wrapper
├── controller/
│   └── library_view_controller.dart       # NEW: split view-only state
├── domain/
│   └── enums/
│       └── library_view_mode.dart         # NEW: tree | list | grid
└── utils/
    └── sort_utils.dart                    # NEW: isolate-friendly sort function
```

### 2.2 Files to Modify

```
lib/features/library/
├── controller/file_browser_controller.dart
├── presentation/screens/home_screen.dart
├── presentation/widgets/home/home_header.dart
├── presentation/widgets/home/home_sort_sheet.dart
├── presentation/widgets/file_browser/file_browser_content.dart
├── presentation/widgets/file_browser/file_list_item.dart
└── main.dart  (register singleton controller)
```

---

## 3. Implementation Phases

### Phase 1: Persistence Layer ⭐ (Highest Priority)

**Goal:** View mode, sort, and filter must survive app restarts.

#### 3.1.1 Create `LibraryPreferencesService`

```dart
class LibraryPreferencesService {
  static const _viewModeKey = 'library_view_mode';
  static const _sortByKey = 'library_sort_by';
  static const _sortOrderKey = 'library_sort_order';
  static const _showOnlyVideosKey = 'library_show_only_videos';

  static late SharedPreferences _prefs;
  static Future<void> init() async => _prefs = await SharedPreferences.getInstance();

  static LibraryViewMode get viewMode { ... }
  static Future<void> setViewMode(LibraryViewMode mode) async { ... }
  // ... same pattern for sortBy, sortOrder, showOnlyVideos
}
```

- Add `LibraryPreferencesService.init()` to `main.dart` alongside the other `init()` calls.

#### 3.1.2 Update `FileBrowserController`

- Remove hard-coded defaults:
  ```dart
  // BEFORE
  bool _isGridView = false;
  SortBy _sortBy = SortBy.name;
  SortOrder _sortOrder = SortOrder.ascending;
  bool _showOnlyVideos = true;
  ```
- Load from `LibraryPreferencesService` in `initialize()`:
  ```dart
  _viewMode = LibraryPreferencesService.viewMode;
  _sortBy = LibraryPreferencesService.sortBy;
  _sortOrder = LibraryPreferencesService.sortOrder;
  _showOnlyVideos = LibraryPreferencesService.showOnlyVideos;
  ```
- Persist on every change:
  ```dart
  void setViewMode(LibraryViewMode mode) {
    _viewMode = mode;
    unawaited(LibraryPreferencesService.setViewMode(mode));
    notifyListeners();
  }
  // repeat for setSortBy, toggleShowOnlyVideos, etc.
  ```

### Phase 2: Split View State from Business Logic

**Goal:** Reduce rebuilds; let UI subscribe only to what it needs.

#### 3.2.1 Create `LibraryViewController`

```dart
class LibraryViewController extends ChangeNotifier {
  LibraryViewMode _viewMode;
  // ... getters + setters that persist via LibraryPreferencesService
}
```

#### 3.2.2 Update `HomeScreen` Widget Tree

```dart
// Provide both controllers separately
MultiProvider(
  providers: [
    ChangeNotifierProvider(create: (_) => FileBrowserController()),
    ChangeNotifierProvider(create: (_) => LibraryViewController()),
  ],
  child: ...
)
```

- `HomeHeader` → watches `LibraryViewController` only
- `FileBrowserContent` → watches `FileBrowserController` for items, `LibraryViewController` for layout
- This prevents the header from rebuilding when items change, and vice-versa.

### Phase 3: Performance Optimizations

#### 3.3.1 Isolate-Based Sorting (`utils/sort_utils.dart`)

```dart
Future<List<FileItem>> sortFileItemsIsolate(
  List<FileItem> items,
  SortBy sortBy,
  SortOrder sortOrder,
) async {
  if (items.length < 100) {
    // Main thread is fine for small lists
    return _sortInPlace(items, sortBy, sortOrder);
  }
  return Isolate.run(() => _sortInPlace(items, sortBy, sortOrder));
}
```

- Update `FileBrowserController._sortItems()` to `await sortFileItemsIsolate(...)`.

#### 3.3.2 Eliminate Recursive FS Scans

- **Current:** `_countVideosRecursively()` walks the filesystem even when an index exists.
- **Fix:** Rely on `LibraryIndexService` snapshot counts first. Only fall back to FS scan if the folder is **not** in the index AND the index is stale.
- Add an `isIndexed` check before spawning `_countVideosRecursively`.

#### 3.3.3 Debounce `notifyListeners()` During Batch Hydration

- In `_hydrateFolderVisibilityBatch`, collect all folders to hide, then call `notifyListeners()` **once** after the batch completes instead of after every sub-batch.

#### 3.3.4 Add `RepaintBoundary` to Expensive Widgets

```dart
// In FileListItem._buildIcon & _GridItem._buildPreview
RepaintBoundary(
  child: LazyThumbnail(...),
)
```

- Also wrap `_GridItem` root in `RepaintBoundary` so scrolling doesn’t repaint stationary items.

#### 3.3.5 Singleton `FileBrowserController`

- Do **not** create a new controller in `HomeScreen.initState()`.
- Register as a lazy singleton via Provider/ GetIt at app startup, or keep it alive with `AutomaticKeepAliveClientMixin` / `IndexedStack` in `MainScreen`.
- Benefit: cache, thumbnails, and index survive tab switches.

#### 3.3.6 Grid Layout Cache

- In `FileBrowserContent._buildGridView()`, cache the `crossAxisCount` / `mainAxisExtent` calculation in a `ValueNotifier` or `MediaQuery` listener so it isn’t recomputed on every item selection change.

#### 3.3.7 Directory Listing Caching Improvements

- Add a TTL (e.g., 30 seconds) to `DirectoryBrowser._cache` so rapid back-and-forth navigation is instant.
- Invalidate on file-system events (already partially done via watcher).

### Phase 4: New View Modes

#### 3.4.1 Define `LibraryViewMode`

```dart
enum LibraryViewMode {
  tree,   // expandable folder hierarchy
  list,   // current vertical list (folders + files)
  grid,   // current 2-4 column grid
}
```

#### 3.4.2 Update `HomeHeader`

- Replace the single `Icons.grid_view_rounded` / `Icons.view_list_rounded` toggle with a **popup menu button** or **segmented button** offering 3 choices:
  - `Icons.account_tree_rounded` → Tree
  - `Icons.view_list_rounded` → List
  - `Icons.grid_view_rounded` → Grid

#### 3.4.3 Build `TreeView` Widget

```dart
// presentation/widgets/file_browser/library_tree_view.dart
class LibraryTreeView extends StatefulWidget {
  final String rootPath;
  final void Function(String path) onFolderTap;
  final void Function(String path) onVideoTap;
  // ...
}
```

- Use a recursive `ListView` with `ExpansionTile` or custom indent levels.
- Load children lazily when a node expands.
- Re-use `LazyThumbnail` for video leaf nodes.
- Cache expanded state in a `Set<String>` so the tree remembers which branches are open during the session.

#### 3.4.4 Update `FileBrowserContent`

```dart
Widget build(BuildContext context) {
  switch (viewMode) {
    case LibraryViewMode.tree:
      return LibraryTreeView(...);
    case LibraryViewMode.list:
      return _buildListView(items);
    case LibraryViewMode.grid:
      return _buildGridView(items);
  }
}
```

### Phase 5: Polish & Edge Cases

#### 3.5.1 Home Skeleton Loader

- `HomeSkeletonLoader` already accepts `isGridView`; extend it to accept `LibraryViewMode` and render a tree shimmer (simple indented bars) when in tree mode.

#### 3.5.2 Sort Sheet

- `HomeSortSheet` is already clean. Ensure it reads the current sort from the controller (which now loads from prefs) so the correct arrow is highlighted on first open.

#### 3.5.3 Selection Mode

- Selection mode should work in all three views. Ensure `LibraryTreeView` supports multi-select leaf nodes.

#### 3.5.4 Migration / Backward Compatibility

- First app launch after update: no saved prefs exist → default to `LibraryViewMode.tree` (matching user’s statement that tree is the desired default).

---

## 4. Expected Outcomes

| Metric | Before | After |
|---|---|---|
| View mode reset on restart | Yes | **No** |
| Sort order reset on restart | Yes | **No** |
| Filter reset on restart | Yes | **No** |
| Available views | list / grid | **tree / list / grid** |
| Sorting large folders | Main-thread jank | **Isolate, 60fps** |
| Folder hydration | 3-4 rebuilds | **1 rebuild + cache** |
| Controller lifetime | Per-screen | **Singleton / Keep-alive** |
| Thumbnail repaints | Unbounded | **RepaintBoundary contained** |

---

## 5. Quick-Win Order (if implementing incrementally)

1. **Create `LibraryPreferencesService`** + wire into controller (fixes the reset bug immediately)
2. **Singleton controller** in `main.dart` (biggest perceived speed-up)
3. **Isolate sort** (removes jank on large folders)
4. **RepaintBoundary** on thumbnails (smoother scrolling)
5. **Tree view widget** (new feature)
6. **Header UI** with 3-way selector

---

*Plan generated for the MPx Player library feature.*
