# 🏗️ MPx Player - Architecture Overview

This document provides a comprehensive overview of the MPx Player architecture, data flows, and design patterns.

**Last Updated:** February 20, 2026  
**Status:** Production-ready foundation with comprehensive testing

---

## 📐 Architecture Overview

MPx Player follows **Clean Architecture** with **Feature-Based Organization**:

```
┌─────────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                          │
│  (UI Components - Screens and Widgets)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │  Home Screen     │  │  Player Screen   │  │  Favorites   │ │
│  │                  │  │                  │  │  Screen      │ │
│  │  - Folder list   │  │  - Video         │  │  - Fav list  │ │
│  │  - Grid/List     │  │    playback      │  │  - Toggle    │ │
│  │  - Pull refresh  │  │  - Gestures      │  │  - Play      │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
│           ↓                      ↓                    ↓        │
│     Consumer<>             Consumer<>           Consumer<>    │
│     context.watch()        context.watch()      context.watch()│
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                     CONTROLLER LAYER                            │
│  (Business Logic - ChangeNotifier implementations)             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │ LibraryController   │  │ PlayerController                │  │
│  │                     │  │                                 │  │
│  │ - Load videos       │  │ extends ChangeNotifier          │  │
│  │ - Refresh storage   │  │ with GestureHandlerMixin,       │  │
│  │ - Lazy loading      │  │      SubtitleManagerMixin,      │  │
│  │ - View mode         │  │      PlaybackControlMixin       │  │
│  │ - Cache management  │  │                                 │  │
│  └─────────────────────┘  │ - Play/pause/seek               │  │
│                           │ - Speed control                 │  │
│                           │ - Volume/brightness             │  │
│                           │ - Position tracking             │  │
│                           │ - Auto-save history             │  │
│                           └─────────────────────────────────┘  │
│                                                                 │
│  Note: Favorites uses Service pattern (static methods)          │
│        No controller needed - simple CRUD operations            │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN LAYER                                 │
│  (Entity Models - Pure Dart, no dependencies)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐            │
│  │  Repository      │         │  Entity Models   │            │
│  │  Interfaces      │         │                  │            │
│  │                  │         │ - VideoFile      │            │
│  │ - PlayerRepo     │         │ - VideoFolder    │            │
│  │   (abstract)     │         │                  │            │
│  └──────────────────┘         └──────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                      DATA LAYER                                 │
│  (Implementations - External dependencies)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ MediaKitPlayer   │  │ VideoScanner     │  │ Services     │  │
│  │ Repository       │  │                  │  │              │  │
│  │                  │  │ - Scan storage   │  │ - Favorites  │  │
│  │ - media_kit      │  │ - Multi-cache    │  │   Service    │  │
│  │ - Player control │  │ - Real-time      │  │ - PlayHistory│  │
│  │ - Streams        │  │   watching       │  │   Service    │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
│  Storage:                                                      │
│  ┌────────────────────┐  ┌────────────────────┐                │
│  │ SQLite (sqflite)   │  │ SharedPreferences  │                │
│  │ - Favorites table  │  │ - Settings         │                │
│  │ - Watch history    │  │ - Subtitle prefs   │                │
│  └────────────────────┘  └────────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow: Complete Journey

### Scenario 1: App Launch to Video Display

```
1. User opens app
   ↓
2. main.dart → MultiProvider initializes LibraryController
   ↓
3. HomeScreen builds → Calls controller.load()
   ↓
4. LibraryController._loadVideos(forceRefresh: false)
   ↓
5. VideoScanner.scanForVideos()
   ├─ Check memory cache (instant if available)
   ├─ Check SQLite cache (fast if available)
   └─ Full scan (slow, only if needed)
       ↓
       ScanOrchestrator.scan()
       └─ Uses PhotoManager (MediaStore API)
           └─ Groups videos by folder
               ↓
6. Returns List<VideoFolder>
   ↓
7. LibraryController updates _folders
   ↓
8. notifyListeners() called
   ↓
9. HomeScreen rebuilds with Consumer<LibraryController>
   ↓
10. UI displays folders in ListView/GridView
```

### Scenario 2: User Opens Folder

```
1. User taps folder
   ↓
2. Navigator.push(FolderDetailScreen)
   ↓
3. FolderDetailScreen calls controller.loadFolderVideos(folderPath)
   ↓
4. LibraryController checks _folderVideoCache
   ├─ Cache hit → Return immediately
   └─ Cache miss → Call _scanner.getVideosInFolder()
       ↓
       Return List<VideoFile>
       ↓
5. Cache results in _folderVideoCache
   ↓
6. Return videos to UI
   ↓
7. FolderDetailScreen displays videos
```

### Scenario 3: Video Playback

```
1. User taps video
   ↓
2. Navigator.push(VideoPlayerScreen(video: video))
   ↓
3. VideoPlayerScreen creates ChangeNotifierProvider
   └─ Creates PlayerController(MediaKitPlayerRepository())
       ↓
4. PlayerController constructor:
   ├─ Initializes mixins
   ├─ Calls initializeSubtitles()
   └─ Sets up stream listeners
       ↓
5. controller.loadVideoFile(video) called
   ├─ Sets _currentVideo
   ├─ Calls repository.load(video.path)
   ├─ Applies subtitle settings
   └─ Starts auto-save timer
       ↓
6. MediaKitPlayerRepository loads video
   └─ media_kit Player opens video
       ↓
7. Streams emit updates:
   ├─ positionStream → Updates _state.position
   ├─ durationStream → Updates _state.duration
   ├─ playingStream → Updates _state.isPlaying
   └─ bufferingStream → Updates _state.isBuffering
       ↓
8. Each stream update calls notifyListeners()
   ↓
9. VideoPlayerScreen rebuilds
   └─ Shows updated position, controls, etc.
       ↓
10. User interacts:
    ├─ Tap → togglePlayPause()
    ├─ Horizontal drag → seek
    ├─ Vertical drag → volume/brightness
    └─ Long press → 2x speed
       ↓
11. Every 30 seconds (if playing):
    └─ Auto-save position to PlayHistoryService
       ↓
12. User exits → PlayerController.dispose()
    ├─ Cancel auto-save timer
    ├─ Force save final position
    ├─ Disable wakelock
    └─ Dispose repository
```

---

## 📦 Feature Organization

Each feature is self-contained with its own:
- **Controller** - Business logic and state (ChangeNotifier)
- **Data** - Repositories and data sources
- **Domain** - Entity models and repository interfaces
- **Presentation** - Screens and widgets
- **Services** - Feature-specific utilities

### Feature: Library

```
lib/features/library/
├── controller/
│   └── library_controller.dart       # Main controller (22 tests ✅)
├── data/
│   ├── datasources/
│   │   └── local_video_scanner.dart  # Video scanning
│   ├── workers/
│   │   └── video_metadata_worker.dart # Background processing
│   └── services/
│       ├── thumbnail_generator.dart
│       └── persistent_cache_service.dart
├── domain/
│   └── entities/
│       ├── video_file.dart           # Video model
│       └── video_folder.dart         # Folder model
└── presentation/
    ├── screens/
    │   ├── home_screen.dart
    │   └── folder_detail_screen.dart
    └── widgets/
        ├── video_list_item.dart
        ├── folder_card.dart
        └── ...
```

**Key Responsibilities:**
- Scan device storage for videos
- Organize videos into folders
- Lazy load folder contents
- Manage view mode (list/grid)
- Handle pull-to-refresh

**State Properties:**
```dart
List<VideoFolder> _folders          // All video folders
bool _isLoading                      // Loading state
bool _isGridView                     // View mode
Map<String, List<VideoFile>> _folderVideoCache  // Lazy loading cache
```

### Feature: Player

```
lib/features/player/
├── controller/
│   ├── player_controller.dart        # Main controller
│   ├── player_state.dart             # State holder
│   └── mixins/
│       ├── gesture_handler_mixin.dart      # Swipe gestures
│       ├── playback_control_mixin.dart     # Play/pause/seek
│       └── subtitle_manager_mixin.dart     # Subtitle settings
├── data/
│   └── repositories/
│       └── media_kit_player_repository.dart
├── domain/
│   └── repositories/
│       └── player_repository.dart    # Abstract interface
├── presentation/
│   ├── screens/
│   │   └── video_player_screen.dart
│   └── widgets/
│       ├── player_controls.dart
│       ├── gesture_detector.dart
│       ├── seek_bar.dart
│       └── ...
└── services/
    ├── play_history_service.dart     # Save/restore position
    └── last_played_service.dart
```

**Architecture Pattern:** Mixins for shared functionality

```dart
class PlayerController extends ChangeNotifier
    with GestureHandlerMixin,
         SubtitleManagerMixin,
         PlaybackControlMixin {
  
  final PlayerRepository _repository;
  final PlayerState _state = PlayerState();
  
  // Streams from repository update UI automatically
  void _setupListeners() {
    _repository.positionStream.listen((pos) {
      if (!_state.isDraggingX) {
        _state.position = pos;
        notifyListeners();
      }
    });
  }
}
```

**Mixins Breakdown:**
- **GestureHandlerMixin**: Horizontal/vertical drag, long press
- **PlaybackControlMixin**: Play, pause, seek, speed, volume
- **SubtitleManagerMixin**: Enable/disable, apply settings

### Feature: Favorites

Uses **Service Pattern** (no controller needed):

```
lib/features/favorites/
├── services/
│   └── favorites_service.dart        # Static methods
├── data/
│   └── repositories/
│       └── favorites_repository.dart
└── presentation/
    └── screens/
        └── favorites_screen.dart
```

**Why Service Pattern?**
- Simple CRUD operations
- No complex state to manage
- Static methods are sufficient

```dart
class FavoritesService {
  static Future<void> toggleFavorite(VideoFile video) async { ... }
  static bool isFavorite(String videoPath) { ... }
  static Future<List<VideoFile>> getAllFavorites() async { ... }
}
```

---

## 🗄️ Data Persistence Architecture

### Storage Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    DATA PERSISTENCE                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ SQLite (sqflite)     │  │ SharedPreferences    │        │
│  │                      │  │                      │        │
│  │ - Favorites          │  │ - Settings           │        │
│  │ - Watch history      │  │ - Subtitle prefs     │        │
│  │ - Video cache        │  │ - Last played        │        │
│  │                      │  │                      │        │
│  │ Tables:              │  │ Keys:                │        │
│  │ - favorites          │  │ - subtitle_enabled   │        │
│  │ - watch_history      │  │ - subtitle_font_size │        │
│  │ - video_cache        │  │ - subtitle_color_*   │        │
│  │                      │  │ - subtitle_bg        │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                                                             │
│  Access Pattern:                                           │
│  FavoritesRepository → SQLite → Device storage             │
│  PlayHistoryService → SQLite → Device storage              │
│  SubtitleSettingsService → SharedPreferences → Device      │
└─────────────────────────────────────────────────────────────┘
```

### Multi-Tier Caching (Video Scanning)

```
VideoScanner.scanForVideos()
    ↓
┌──────────────────────┐
│ Tier 1: Memory Cache │  (Instant)
│ _cachedFolders       │
│ Speed: ~0ms          │
└──────────────────────┘
    ↓ (if miss)
┌──────────────────────┐
│ Tier 2: SQLite Cache │  (Very Fast)
│ PersistentCacheService
│ Speed: ~50-100ms     │
└──────────────────────┘
    ↓ (if miss)
┌──────────────────────┐
│ Tier 3: Full Scan    │  (Slow)
│ PhotoManager API     │
│ Speed: ~1-2 seconds  │
│ (First time only)    │
└──────────────────────┘
```

---

## 🔄 State Management Pattern

### Provider Hierarchy

```
main.dart
    ↓
MultiProvider (App-Level, Persistent)
    ├─ LibraryController(VideoScanner())
    │   └─ Singleton-like: Created once, persists for app lifetime
    │
    └─ (Future: Additional app-wide controllers)

Navigation
    ↓

Screen-Level Providers (Created/Disposed per screen)
    ↓
VideoPlayerScreen
    ↓
ChangeNotifierProvider
    └─ PlayerController(MediaKitPlayerRepository())
        └─ Created on screen open
        └─ Disposed on screen close
```

### State Update Flow

```
User Action (e.g., pull-to-refresh)
    ↓
LibraryController.refresh()
    ↓
_scanner.scanForVideos(forceRefresh: true)
    ↓
Returns new List<VideoFolder>
    ↓
_folders = newFolders
    ↓
notifyListeners()
    ↓
Consumer<LibraryController> widgets rebuild
    ↓
UI updates with new data
```

---

## 🧪 Testing Architecture

### Test Structure

```
test/
├── mocks/                          # Mock definitions
│   ├── video_scanner_mock.dart     # @GenerateMocks([VideoScanner])
│   └── player_repository_mock.dart # @GenerateMocks([PlayerRepository])
│
├── unit/                           # Unit tests
│   └── controllers/
│       ├── library_controller_test.dart   # ✅ 22 tests passing
│       └── player_controller_test.dart    # ✅ Comprehensive tests
│
└── (Future: widget/, integration/)
```

### Testing Pattern

```dart
// 1. Mock dependencies
class MockVideoScanner extends Mock implements VideoScanner {}

// 2. Set up test
group('LibraryController', () {
  late LibraryController controller;
  late MockVideoScanner mockScanner;
  
  setUp(() {
    mockScanner = MockVideoScanner();
    controller = LibraryController(mockScanner);
  });
  
  // 3. Test scenarios
  test('should load folders successfully', () async {
    // Arrange
    when(mockScanner.scanForVideos(...))
        .thenAnswer((_) async => [testVideoFolder]);
    
    // Act
    await controller.load();
    
    // Assert
    expect(controller.folders, [testVideoFolder]);
    expect(controller.isLoading, false);
  });
});
```

### Current Test Coverage

```
✅ LibraryController Tests: 22 passing
   - Initial state verification
   - Load/refresh functionality
   - View mode toggle
   - Lazy loading
   - Cache management
   - Error handling
   - Edge cases

✅ PlayerController Tests: Structure complete
   - Video loading
   - Playback controls
   - Stream listeners
   - Gesture handling
   - Position saving

🎯 Target: 60%+ overall coverage
```

---

## 🔐 Key Design Patterns

### 1. Repository Pattern

**Purpose**: Abstract data access for testability

```dart
// Domain - Interface
abstract class PlayerRepository {
  Future<void> load(String path);
  Future<void> play();
  Stream<Duration> get positionStream;
}

// Data - Implementation
class MediaKitPlayerRepository implements PlayerRepository {
  final Player _player;
  
  @override
  Future<void> load(String path) async {
    await _player.open(Media(path));
  }
}

// Controller - Depends on abstraction
class PlayerController {
  final PlayerRepository _repository;
  
  PlayerController(this._repository); // Inject mock or real
}
```

### 2. ChangeNotifier + Provider

**Purpose**: Reactive state management

```dart
class LibraryController extends ChangeNotifier {
  List<VideoFolder> _folders = [];
  
  List<VideoFolder> get folders => _folders;
  
  Future<void> load() async {
    _folders = await _scanner.scanForVideos();
    notifyListeners(); // Triggers UI rebuild
  }
}

// UI
Consumer<LibraryController>(
  builder: (context, controller, child) {
    return ListView(
      children: controller.folders.map(...).toList(),
    );
  },
)
```

### 3. Mixin Pattern

**Purpose**: Break large controllers into logical units

```dart
mixin PlaybackControlMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;
  
  void togglePlayPause() {
    state.isPlaying ? repository.pause() : repository.play();
    notifyListeners();
  }
}

class PlayerController extends ChangeNotifier
    with PlaybackControlMixin, GestureHandlerMixin {
  // Gets all mixin methods
}
```

### 4. Lazy Loading with Cache

**Purpose**: Performance optimization

```dart
Future<List<VideoFile>> loadFolderVideos(String folderPath) async {
  // Check cache first
  if (_folderVideoCache.containsKey(folderPath)) {
    return _folderVideoCache[folderPath]!; // Instant return
  }
  
  // Load from data source
  final videos = await _scanner.getVideosInFolder(folderPath);
  
  // Cache for next time
  _folderVideoCache[folderPath] = videos;
  _loadedFolders[folderPath] = true;
  
  return videos;
}
```

---

## 📱 Platform-Specific Considerations

### Android
- **MediaStore API** via PhotoManager for fast scanning
- **Storage permissions** (READ_MEDIA_VIDEO for Android 13+)
- **Background playback** support
- **PiP mode** (future)

### iOS
- **Photo Library** access via PhotoManager
- **App Sandbox** restrictions respected
- **Background audio** (future)

### Shared (Flutter)
- **UI** - All Flutter widgets
- **Business Logic** - Pure Dart (controllers)
- **Data Persistence** - sqflite + SharedPreferences

---

## 🎯 Performance Optimizations

### Implemented

1. **Multi-tier caching** - Memory → SQLite → Disk
2. **Lazy loading** - Folder contents load on-demand
3. **Background processing** - Thumbnail generation
4. **Debounced saves** - Watch history saved every 30s, not every frame
5. **Efficient rebuilds** - notifyListeners() only when necessary

### Future

1. **List virtualization** - For large folders
2. **Image caching** - Thumbnail LRU cache
3. **Query optimization** - Database indexing

---

## 🚀 Offline-First Design

This app is built with **privacy and offline operation** as core principles:

- ✅ **No network requests** - Everything works offline
- ✅ **Local storage only** - SQLite + SharedPreferences
- ✅ **No cloud dependencies** - No Firebase, no analytics
- ✅ **Open source** - Transparent and auditable

**Error Reporting:** Users can report bugs via GitHub issues with:
- Device info (manually provided)
- Steps to reproduce
- Local error logs (if implemented)

---

## 📚 Related Documentation

| Document | Description |
|----------|-------------|
| **[APP_UNDERSTANDING_GUIDE.md](APP_UNDERSTANDING_GUIDE.md)** | Complete learning roadmap for the codebase |
| **[README.md](README.md)** | Main project documentation |
| **[PRODUCTION_ROADMAP.md](PRODUCTION_ROADMAP.md)** | Development roadmap and timeline |
| **[PRODUCTION_STATUS_REPORT.md](PRODUCTION_STATUS_REPORT.md)** | Current status and metrics |

---

**Architecture Questions?** Check the [APP_UNDERSTANDING_GUIDE.md](APP_UNDERSTANDING_GUIDE.md) for detailed explanations of each component.

---

*Last updated: February 20, 2026*
