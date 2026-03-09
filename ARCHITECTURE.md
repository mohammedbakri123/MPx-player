# 🏗️ MPx Player - Architecture Overview

This document provides a comprehensive overview of the MPx Player architecture, data flows, and design patterns.

**Last Updated:** March 9, 2026  
**Status:** Production-ready foundation with persistent indexing and comprehensive testing

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
│  ┌────────────────────────┐  ┌─────────────────────────────────┐  │
│  │ FileBrowserController  │  │ PlayerController                │  │
│  │                        │  │                                 │  │
│  │ - Load directories     │  │ extends ChangeNotifier          │  │
│  │ - Refresh storage      │  │ with GestureHandlerMixin,       │  │
│  │ - Lazy loading         │  │      SubtitleManagerMixin,      │  │
│  │ - View mode            │  │      PlaybackControlMixin       │  │
│  │ - Cache management     │  │                                 │  │
│  └────────────────────────┘  │ - Play/pause/seek               │  │
│                              │ - Speed control                 │  │
│                              │ - Volume/brightness             │  │
│                              │ - Position tracking             │  │
│                              │ - Auto-save history             │  │
│                              └─────────────────────────────────┘  │
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
│  │ MediaKitPlayer   │  │ DirectoryBrowser │  │ Services     │  │
│  │ Repository       │  │                  │  │              │  │
│  │                  │  │ - Scan storage   │  │ - Favorites  │  │
│  │ - media_kit      │  │ - Multi-cache    │  │   Service    │  │
│  │ - Player control │  │ - Real-time      │  │ - History    │  │
│  │ - Streams        │  │   watching       │  │   Service    │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
│  Storage:                                                      │
│  ┌────────────────────┐  ┌────────────────────┐                │
│  │ SQLite (sqflite)   │  │ SharedPreferences  │                │
│  │ - Favorites table  │  │ - Settings         │                │
│  │ - Watch history    │  │ - Subtitle prefs   │                │
│  │ - Library index    │  │                    │                │
│  └────────────────────┘  └────────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow: Complete Journey

### Scenario 1: App Launch to Video Display

```
1. User opens app
   ↓
2. main.dart → MultiProvider initializes FileBrowserController
   ↓
3. HomeScreen builds → Calls controller.initialize()
   ↓
4. FileBrowserController.loadDirectory(rootPath)
   ↓
5. LibraryIndexService.ensureIndexed(rootPath)
   ├─ Check memory cache (_snapshots map)
   ├─ Check SQLite index (library_index_metadata table)
   └─ Full scan (recursively list directories, only if needed)
       ↓
       _buildIndex(rootPath)
       └─ Uses Directory.list()
           └─ Groups videos by folder and persists to SQLite
               ↓
6. Returns LibraryIndexSnapshot
   ↓
7. FileBrowserController updates _items with filtered folders/videos
   ↓
8. notifyListeners() called
   ↓
9. HomeScreen rebuilds with Consumer<FileBrowserController>
   ↓
10. UI displays folders in ListView/GridView
```

### Scenario 2: User Opens Folder

```
1. User taps folder
   ↓
2. FileBrowserController.navigateToFolder(folder)
   ↓
3. loadDirectory(folder.path)
   ↓
4. DirectoryBrowser.listDirectory(path)
   ├─ Cache hit → Return immediately
   └─ Cache miss → Call Directory.list()
       ↓
       Return List<FileItem>
       ↓
5. Cache results in DirectoryBrowser._cache
   ↓
6. Filter empty folders if requested using LibraryIndexService
   ↓
7. notifyListeners()
   ↓
8. UI displays items in the folder
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
    └─ Auto-save position to HistoryService (SQLite)
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
│   └── file_browser_controller.dart  # Main controller
├── data/
│   └── datasources/
│       └── directory_browser.dart    # Directory listing & caching
├── domain/
│   └── entities/
│       ├── file_item.dart            # Generic file/folder model
│       ├── video_file.dart           # Video model
│       └── video_folder.dart         # Folder model
├── presentation/
│   ├── screens/
│   │   ├── home_screen.dart
│   │   └── search_screen.dart
│   └── widgets/
│       ├── file_browser/             # File browser UI
│       ├── video/                    # Video list/thumbnail widgets
│       └── ...
└── services/
    └── library_index_service.dart    # Persistent indexing logic
```

**Key Responsibilities:**
- Scan device storage for videos
- Persist library index in SQLite
- Provide instant search across all indexed videos
- Manage view mode (list/grid)
- Filter empty folders (those without videos)
- Handle pull-to-refresh (invalidates index and rescans)

### Feature: Player

```
lib/features/player/
├── controller/
│   ├── player_controller.dart        # Main controller
│   ├── player_state.dart             # State holder
│   └── mixins/                       # Mixins for granular logic
├── data/
│   └── repositories/
│       └── media_kit_player_repository.dart
├── domain/
│   └── repositories/
│       └── player_repository.dart    # Abstract interface
├── presentation/
│   ├── screens/
│   │   └── video_player_screen.dart
│   └── widgets/                      # Player UI components
└── services/
    └── ...
```

### Feature: Favorites

Uses **Service Pattern** (static methods) with SQLite persistence:

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
│  │ - Favorites table    │  │ - Settings           │        │
│  │ - Watch history      │  │ - Subtitle prefs     │        │
│  │ - Library index      │  │                      │        │
│  │                      │  │                      │        │
│  │ Tables:              │  │ Keys:                │        │
│  │ - favorites          │  │ - subtitle_enabled   │        │
│  │ - watch_history      │  │ - subtitle_font_size │        │
│  │ - videos             │  │ - ...                │        │
│  │ - folders            │  │                      │        │
│  │ - library_metadata   │  │                      │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                                                             │
│  Access Pattern:                                           │
│  FavoritesService → SQLite → Device storage                │
│  HistoryService → SQLite → Device storage                  │
│  LibraryIndexService → SQLite → Device storage             │
└─────────────────────────────────────────────────────────────┘
```

### Multi-Tier Caching (Library Indexing)

```
LibraryIndexService.ensureIndexed()
    ↓
┌──────────────────────┐
│ Tier 1: Memory Cache │  (Instant)
│ _snapshots map       │
│ Speed: ~0ms          │
└──────────────────────┘
    ↓ (if miss)
┌──────────────────────┐
│ Tier 2: SQLite Index │  (Fast)
│ videos/folders tables│
│ Speed: ~100-300ms    │
└──────────────────────┘
    ↓ (if miss)
┌──────────────────────┐
│ Tier 3: Disk Scan    │  (Slower)
│ Directory.list()     │
│ Speed: ~1-5 seconds  │
│ (Only once per root) │
└──────────────────────┘
```

---

## 🔄 State Management Pattern

### Provider Hierarchy

```
main.dart
    ↓
MultiProvider (App-Level, Persistent)
    ├─ FileBrowserController()
    │   └─ Singleton-like: Created once, persists for app lifetime
    │
    └─ ...

Navigation
    ↓

Screen-Level Providers (Created/Disposed per screen)
    ↓
VideoPlayerScreen
    ↓
ChangeNotifierProvider
    └─ PlayerController(MediaKitPlayerRepository())
```

---

## 🧪 Testing Architecture

### Current Status

✅ **FileBrowserController Tests**: Passing (Renamed from LibraryController)
✅ **PlayerController Tests**: Passing
✅ **LibraryIndexService**: New logic covered by existing patterns

🎯 **Target**: Maintain high test coverage while evolving the indexing system.

---

## 🚀 Offline-First Design

This app is built with **privacy and offline operation** as core principles:

- ✅ **No network requests** - Everything works offline
- ✅ **Local storage only** - SQLite + SharedPreferences
- ✅ **No cloud dependencies** - No Firebase, no analytics
- ✅ **Open source** - Transparent and auditable

---

**Architecture Questions?** Check the [APP_UNDERSTANDING_GUIDE.md](APP_UNDERSTANDING_GUIDE.md) for detailed explanations of each component.

---

*Last updated: March 9, 2026*
