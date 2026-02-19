# MPx Player - Complete App Understanding Guide

## 📚 Learning Roadmap (Complete in Order)

### Phase 1: High-Level Architecture (30 min)
**Goal**: Understand how the app is organized

### Phase 2: Data Flow Deep Dive (1 hour)
**Goal**: Trace how videos get from storage to screen

### Phase 3: Feature Deep Dive (2 hours)
**Goal**: Understand each feature's implementation

### Phase 4: Code Patterns (30 min)
**Goal**: Learn the coding conventions used

---

## 🏗️ Phase 1: High-Level Architecture

### Project Structure

```
MPx Player
├── lib/
│   ├── main.dart                    # Entry point, routes setup
│   ├── core/                        # Shared code used everywhere
│   │   ├── database/               # SQLite database setup
│   │   ├── services/               # Logger, permissions
│   │   ├── utils/                  # Debouncer, LRU cache
│   │   └── widgets/                # MainScreen, PermissionWrapper
│   │
│   └── features/                   # Each feature is self-contained
│       ├── library/                # 📁 Video library feature
│       ├── player/                 # 🎬 Video player feature
│       ├── favorites/              # ⭐ Favorites feature
│       ├── settings/               # ⚙️  Settings feature
│       └── splash/                 # 🚀 Splash screen
│
└── test/                           # Unit tests
```

### Key Principle: Clean Architecture

Each feature follows this pattern:

```
feature/
├── controller/          # Business logic & state (ChangeNotifier)
├── domain/
│   ├── entities/       # Data models (VideoFile, VideoFolder)
│   └── repositories/   # Abstract interfaces
├── data/
│   ├── repositories/   # Concrete implementations
│   └── datasources/    # Data sources (scanner, database)
├── presentation/
│   ├── screens/        # Full screens
│   └── widgets/        # Reusable UI components
└── services/           # Feature-specific services
```

**Why this matters**: Separation of concerns makes code testable and maintainable

---

## 🔄 Phase 2: Data Flow Deep Dive

### How a Video Gets to the Screen

```
1. User opens app
   ↓
2. LibraryController.load() called
   ↓
3. VideoScanner.scanForVideos() scans storage
   ↓
4. Videos grouped into VideoFolders
   ↓
5. Folders cached (Memory → SQLite)
   ↓
6. LibraryController notifies UI
   ↓
7. HomeScreen displays folders
   ↓
8. User taps folder
   ↓
9. LibraryController.loadFolderVideos() loads videos
   ↓
10. FolderDetailScreen shows videos
   ↓
11. User taps video
   ↓
12. PlayerController.loadVideoFile() loads video
   ↓
13. MediaKitPlayerRepository plays video
   ↓
14. VideoPlayerScreen shows playback
```

### Detailed Flow

#### Step 1: Video Scanning
**File**: `lib/features/library/data/datasources/local_video_scanner.dart`

```dart
VideoScanner.scanForVideos()
  ├── Check memory cache (instant)
  ├── Check SQLite cache (very fast)
  └── Full scan (slow, only if needed)
      └── ScanOrchestrator.scan()
          └── Uses PhotoManager (MediaStore API)
              └── Groups videos by folder
```

**Key Point**: Multi-tier caching makes app fast (1-2s vs 20s+ on first load)

#### Step 2: State Management
**File**: `lib/features/library/controller/library_controller.dart`

```dart
LibraryController extends ChangeNotifier
  ├── _folders: List<VideoFolder>      # All video folders
  ├── _isLoading: bool                 # Loading state
  ├── _folderVideoCache: Map           # Lazy loading cache
  │
  ├── load()                           # Initial load
  ├── refresh()                        # Force refresh
  ├── loadFolderVideos()               # Lazy load folder contents
  └── toggleViewMode()                 # List/Grid toggle
```

**Pattern**: Controller holds state → UI listens → State changes → UI rebuilds

#### Step 3: Repository Pattern
**Abstract Interface**: `lib/features/player/domain/repositories/player_repository.dart`
**Implementation**: `lib/features/player/data/repositories/media_kit_player_repository.dart`

```dart
// Domain (interface)
abstract class PlayerRepository {
  Future<void> load(String path);
  Future<void> play();
  Future<void> pause();
  Stream<Duration> get positionStream;
  // ...
}

// Data (implementation)
class MediaKitPlayerRepository implements PlayerRepository {
  final Player _player;  // media_kit Player instance
  
  @override
  Future<void> load(String path) async {
    await _player.open(Media(path));
  }
  // ...
}
```

**Why**: Can swap implementations (e.g., switch from media_kit to exo_player) without changing UI code

---

## 🎯 Phase 3: Feature Deep Dive

### Feature 1: Video Library

**Purpose**: Browse and organize video folders

**Key Files**:
- Controller: `lib/features/library/controller/library_controller.dart`
- Scanner: `lib/features/library/data/datasources/local_video_scanner.dart`
- UI: `lib/features/library/presentation/screens/home_screen.dart`

**How It Works**:
1. `HomeScreen` initializes → calls `controller.load()`
2. Controller calls `VideoScanner.scanForVideos()`
3. Scanner returns `List<VideoFolder>`
4. UI displays folders in ListView/GridView
5. User pulls to refresh → `controller.refresh()`

**Smart Features**:
- **Lazy Loading**: Folder videos load only when folder opened
- **Caching**: 3-tier cache (Memory → SQLite → Disk)
- **Real-time Updates**: Watches directories for new/deleted videos

### Feature 2: Video Player

**Purpose**: Play videos with gestures and controls

**Key Files**:
- Controller: `lib/features/player/controller/player_controller.dart`
- Mixins: `lib/features/player/controller/mixins/`
- UI: `lib/features/player/presentation/screens/video_player_screen.dart`

**Architecture**:
```dart
PlayerController extends ChangeNotifier
    with GestureHandlerMixin,       // Swipe gestures
         SubtitleManagerMixin,      // Subtitle settings
         PlaybackControlMixin {     // Play/pause/seek
  
  final PlayerRepository _repository;  // MediaKit implementation
  final PlayerState _state;            // All state fields
  
  // Streams from repository update UI automatically
  void _setupListeners() {
    _repository.positionStream.listen((pos) {
      _state.position = pos;
      notifyListeners();  // UI rebuilds
    });
  }
}
```

**Gestures**:
- Horizontal drag → Seek forward/backward
- Vertical drag left → Brightness
- Vertical drag right → Volume
- Long press → 2x speed
- Double tap → Show/hide controls

### Feature 3: Favorites

**Purpose**: Save favorite videos

**Key Files**:
- Service: `lib/features/favorites/services/favorites_service.dart`
- Repository: `lib/features/favorites/data/repositories/favorites_repository.dart`

**Pattern**: Static service class (no controller needed)

```dart
// Simple API
FavoritesService.toggleFavorite(video);
FavoritesService.isFavorite(videoPath);
FavoritesService.getAllFavorites();
```

**Storage**: SQLite database (persistent)

### Feature 4: Settings

**Purpose**: App preferences

**Key Files**:
- Subtitle Settings: `lib/features/settings/services/subtitle_settings_service.dart`
- UI: `lib/features/settings/presentation/screens/settings_screen.dart`

**Storage**: SharedPreferences (key-value pairs)

---

## 🧩 Phase 4: Code Patterns

### Pattern 1: ChangeNotifier + Provider

**Used For**: State management throughout app

```dart
// Controller
class LibraryController extends ChangeNotifier {
  List<VideoFolder> _folders = [];
  
  List<VideoFolder> get folders => _folders;
  
  Future<void> load() async {
    _folders = await _scanner.scanForVideos();
    notifyListeners();  // Tells UI to rebuild
  }
}

// UI
class HomeScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Consumer<LibraryController>(
      builder: (context, controller, child) {
        if (controller.isLoading) return LoadingWidget();
        return ListView(
          children: controller.folders.map(...).toList(),
        );
      },
    );
  }
}
```

### Pattern 2: Repository Pattern

**Used For**: Data access abstraction

```dart
// Domain - defines contract
abstract class PlayerRepository {
  Future<void> load(String path);
  Future<void> play();
}

// Data - implements contract
class MediaKitPlayerRepository implements PlayerRepository {
  @override
  Future<void> load(String path) async {
    // Implementation with media_kit
  }
}

// Controller - depends on abstraction, not implementation
class PlayerController {
  final PlayerRepository _repository;  // Can be any implementation
  
  PlayerController(this._repository);
}
```

### Pattern 3: Mixins for Shared Functionality

**Used For**: Breaking large controllers into logical parts

```dart
// playback_control_mixin.dart
mixin PlaybackControlMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;
  
  void togglePlayPause() {
    if (state.isPlaying) {
      repository.pause();
    } else {
      repository.play();
    }
  }
}

// gesture_handler_mixin.dart
mixin GestureHandlerMixin on ChangeNotifier {
  void onHorizontalDragStart(double startX) { ... }
  void onHorizontalDragUpdate(double currentX, double width) { ... }
}

// Combine in controller
class PlayerController extends ChangeNotifier
    with PlaybackControlMixin, GestureHandlerMixin {
  // Has all mixin methods
}
```

### Pattern 4: Lazy Loading with Caching

**Used For**: Performance optimization

```dart
Future<List<VideoFile>> loadFolderVideos(String folderPath) async {
  // Check cache first
  if (_folderVideoCache.containsKey(folderPath)) {
    return _folderVideoCache[folderPath]!;  // Instant return
  }
  
  // Load from scanner
  final videos = await _scanner.getVideosInFolder(folderPath);
  
  // Cache for next time
  _folderVideoCache[folderPath] = videos;
  
  return videos;
}
```

---

## 🎓 Study Guide: Recommended Reading Order

### Week 1: Foundation
**Day 1**: Read this guide + explore project structure
**Day 2**: Study `VideoScanner` class
**Day 3**: Study `LibraryController` class
**Day 4**: Study `HomeScreen` UI
**Day 5**: Trace complete data flow

### Week 2: Player Deep Dive
**Day 1**: Study `PlayerController` + mixins
**Day 2**: Study `PlayerRepository` interface + implementation
**Day 3**: Study `VideoPlayerScreen` + gestures
**Day 4**: Study `PlayHistoryService`
**Day 5**: Trace player data flow

### Week 3: Advanced Features
**Day 1**: Study caching system (3-tier)
**Day 2**: Study real-time directory watching
**Day 3**: Study favorites system
**Day 4**: Study settings persistence
**Day 5**: Review tests (understand testing patterns)

---

## 🔍 Key Questions to Answer

As you study, answer these:

1. **How does a video get from storage to screen?** (Trace the flow)
2. **What happens when user pulls to refresh?** (Understand cache invalidation)
3. **How are folders organized?** (Scanner grouping logic)
4. **How does lazy loading work?** (Cache implementation)
5. **How do gestures work in player?** (GestureHandlerMixin)
6. **How is state shared between widgets?** (Provider pattern)
7. **How are errors handled?** (try-catch patterns)
8. **How is data persisted?** (SQLite vs SharedPreferences)

---

## 📝 Quick Reference

### File Map by Feature

| Feature | Controller | Repository | UI Screen |
|---------|-----------|------------|-----------|
| **Library** | `library_controller.dart` | `local_video_scanner.dart` | `home_screen.dart` |
| **Player** | `player_controller.dart` | `media_kit_player_repository.dart` | `video_player_screen.dart` |
| **Favorites** | (service pattern) | `favorites_repository.dart` | `favorites_screen.dart` |
| **Settings** | (service pattern) | `subtitle_settings_service.dart` | `settings_screen.dart` |

### Data Models

```dart
VideoFile {
  id, path, title, folderPath, folderName,
  size, duration, dateAdded, width, height,
  thumbnailPath
}

VideoFolder {
  path, name, videos: List<VideoFile>
}
```

### Key Dependencies

- **media_kit**: Video playback (mpv backend)
- **photo_manager**: Access to device media library
- **sqflite**: SQLite database
- **shared_preferences**: Key-value storage
- **provider**: State management
- **go_router**: Navigation

