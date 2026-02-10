# 🏗️ MPx Player - Architecture Overview

This document provides a visual overview of the MPx Player architecture and how all components fit together.

---

## 📐 Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                          │
│  (UI Components - What the user sees and interacts with)       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │  Home Screen     │  │  Player Screen   │  │  Favorites   │ │
│  │                  │  │                  │  │  Screen      │ │
│  │  - Video list    │  │  - Video         │  │  - Fav list  │ │
│  │  - Grid/List     │  │    playback      │  │  - Remove    │ │
│  │  - Search UI     │  │  - Controls      │  │  - Play      │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
│           ↓                      ↓                    ↓        │
│     context.watch()        context.watch()      context.watch()│
│           ↓                      ↓                    ↓        │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                     CONTROLLER LAYER                            │
│  (Business Logic - State management and coordination)          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────────┐   │
│  │ Library       │  │ Player        │  │ Favorites        │   │
│  │ Controller    │  │ Controller    │  │ Controller       │   │
│  │               │  │               │  │                  │   │
│  │ - Load videos │  │ - Play/pause  │  │ - Add favorite   │   │
│  │ - Search      │  │ - Seek        │  │ - Remove         │   │
│  │ - Sort/filter │  │ - Speed       │  │ - Load list      │   │
│  │ - View mode   │  │ - Subtitles   │  │ - Toggle status  │   │
│  └───────────────┘  └───────────────┘  └──────────────────┘   │
│         ↓                    ↓                    ↓            │
│    notifyListeners()   notifyListeners()   notifyListeners()  │
│         ↓                    ↓                    ↓            │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN LAYER (Optional)                      │
│  (Business Rules - Pure Dart, no dependencies)                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐            │
│  │  Repositories    │         │  Entities        │            │
│  │  (Interfaces)    │         │  (Models)        │            │
│  │                  │         │                  │            │
│  │ - PlayerRepo     │         │ - VideoFile      │            │
│  │ - LibraryRepo    │         │ - VideoFolder    │            │
│  │ - FavoritesRepo  │         │ - WatchHistory   │            │
│  └──────────────────┘         └──────────────────┘            │
│          ↑                                                     │
│     implements                                                 │
│          ↓                                                     │
└─────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                      DATA LAYER                                 │
│  (Data Sources - External dependencies)                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │ Media Kit      │  │ Video Scanner  │  │ Hive Database   │  │
│  │ Repository     │  │                │  │                 │  │
│  │                │  │ - Scan files   │  │ - Favorites     │  │
│  │ - Load video   │  │ - Group        │  │ - Watch history │  │
│  │ - Play/pause   │  │   folders      │  │ - Settings      │  │
│  │ - Seek         │  │ - Metadata     │  │                 │  │
│  └────────────────┘  └────────────────┘  └─────────────────┘  │
│         ↓                     ↓                    ↓           │
└─────────────────────────────────────────────────────────────────┘
         ↓                     ↓                    ↓
┌─────────────────────────────────────────────────────────────────┐
│                   EXTERNAL SYSTEMS                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  media_kit   │  │ File System  │  │  Hive Storage        │ │
│  │  (Native)    │  │              │  │  (Local Database)    │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow Example: Playing a Video

```
1. User taps video thumbnail
         ↓
2. HomeScreen → Navigator.push(VideoPlayerScreen)
         ↓
3. VideoPlayerScreen creates ChangeNotifierProvider
         ↓
4. Provider creates PlayerController(MediaKitPlayerRepository)
         ↓
5. controller.loadVideo(videoPath)
         ↓
6. PlayerController → PlayerRepository.load(path)
         ↓
7. MediaKitPlayerRepository → media_kit Player.open()
         ↓
8. Media plays, streams update (position, duration, playing)
         ↓
9. PlayerController listens to streams → notifyListeners()
         ↓
10. UI rebuilds with context.watch<PlayerController>()
         ↓
11. User sees video playing with updated position/controls
         ↓
12. Every 5 seconds: Save progress to WatchHistoryRepository
         ↓
13. User exits → Provider disposes PlayerController
         ↓
14. PlayerController.dispose() → Save final position & cleanup
```

---

## 📦 Dependency Injection Flow

```
main.dart
   ↓
MultiProvider (App Level)
   ├─> LibraryController(VideoScanner())
   ├─> FavoritesController(FavoritesRepository())
   └─> SettingsController(SettingsRepository())
   
   ↓ (Navigate to Video Player)
   
VideoPlayerScreen
   ↓
ChangeNotifierProvider (Screen Level)
   └─> PlayerController(MediaKitPlayerRepository())
       
   ↓ (Screen disposed)
   
Provider calls PlayerController.dispose()
   ↓ Cleanup complete
```

---

## 🗄️ Data Persistence Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    DATABASE SERVICE                         │
│                   (Singleton Instance)                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Repositories                                        │  │
│  │                                                      │  │
│  │  ├─ FavoritesRepository                            │  │
│  │  │    ├─ Box<FavoriteVideo>                        │  │
│  │  │    ├─ addFavorite()                             │  │
│  │  │    ├─ removeFavorite()                          │  │
│  │  │    └─ getAllFavorites()                         │  │
│  │  │                                                  │  │
│  │  ├─ WatchHistoryRepository                         │  │
│  │  │    ├─ Box<WatchHistory>                         │  │
│  │  │    ├─ saveProgress()                            │  │
│  │  │    ├─ getHistory()                              │  │
│  │  │    └─ getContinueWatching()                     │  │
│  │  │                                                  │  │
│  │  └─ SettingsRepository                             │  │
│  │       ├─ SharedPreferences                         │  │
│  │       ├─ setThemeMode()                            │  │
│  │       ├─ setDefaultSpeed()                         │  │
│  │       └─ getAutoPlay()                             │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ↓                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  STORAGE LAYER                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────┐        ┌─────────────────────┐   │
│  │  Hive Boxes         │        │  SharedPreferences  │   │
│  │  (NoSQL Storage)    │        │  (Key-Value Store)  │   │
│  │                     │        │                     │   │
│  │  favorites.hive     │        │  theme_mode         │   │
│  │  watch_history.hive │        │  default_speed      │   │
│  │                     │        │  subtitle_size      │   │
│  └─────────────────────┘        └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          ↓
                   Device Storage
              /data/data/com.mpx.player/
```

---

## 🎯 Feature Organization

```
lib/
├── core/                           # Shared utilities
│   ├── database/
│   │   ├── models/                 # Hive models
│   │   │   ├── favorite_video.dart
│   │   │   └── watch_history.dart
│   │   ├── repositories/           # Data access
│   │   │   ├── favorites_repository.dart
│   │   │   ├── watch_history_repository.dart
│   │   │   └── settings_repository.dart
│   │   └── database_service.dart   # Initialization
│   ├── errors/
│   │   ├── app_error.dart         # Error types
│   │   └── error_handler.dart     # Global handler
│   └── services/
│       ├── logger_service.dart    # Logging
│       └── permission_service.dart # Permissions
│
├── features/
│   ├── library/                    # Video library feature
│   │   ├── controller/
│   │   │   └── library_controller.dart
│   │   ├── data/
│   │   │   └── datasources/
│   │   │       └── local_video_scanner.dart
│   │   ├── domain/
│   │   │   └── entities/
│   │   │       ├── video_file.dart
│   │   │       └── video_folder.dart
│   │   └── presentation/
│   │       └── screens/
│   │           ├── home_screen.dart
│   │           └── folder_detail_screen.dart
│   │
│   ├── player/                     # Video player feature
│   │   ├── controller/
│   │   │   └── player_controller.dart
│   │   ├── data/
│   │   │   └── repositories/
│   │   │       └── media_kit_player_repository.dart
│   │   ├── domain/
│   │   │   └── repositories/
│   │   │       └── player_repository.dart
│   │   └── presentation/
│   │       ├── screens/
│   │       │   └── video_player_screen.dart
│   │       └── widgets/
│   │           ├── player_view.dart
│   │           ├── controls_layer.dart
│   │           └── ...
│   │
│   ├── favorites/                  # Favorites feature
│   │   ├── controller/
│   │   │   └── favorites_controller.dart
│   │   └── presentation/
│   │       └── screens/
│   │           └── favorites_screen.dart
│   │
│   └── settings/                   # Settings feature
│       ├── controller/
│       │   └── settings_controller.dart
│       └── presentation/
│           └── screens/
│               └── settings_screen.dart
│
└── main.dart                       # App entry point
```

---

## 🔐 State Management Pattern

### Provider Hierarchy

```
MaterialApp (ErrorHandler.scaffoldMessengerKey)
   ↓
MultiProvider
   ├─ LibraryController (App-wide, persists)
   ├─ FavoritesController (App-wide, persists)
   └─ SettingsController (App-wide, persists)
   
   ↓ Navigation
   
Scaffold (MainScreen with BottomNav)
   ├─ Tab 1: HomeScreen
   │    └─ Uses LibraryController via context.watch()
   │
   ├─ Tab 2: FavoritesScreen
   │    └─ Uses FavoritesController via context.watch()
   │
   └─ Tab 3: SettingsScreen
        └─ Uses SettingsController via context.watch()
        
   ↓ Tap video
   
Navigator.push(VideoPlayerScreen)
   ↓
ChangeNotifierProvider (Screen-scoped)
   └─ PlayerController (Created on open, disposed on close)
       └─ VideoPlayerScreen uses via context.watch()
```

### Controller Lifecycle

```
APP-LEVEL CONTROLLERS (Singleton-like)
┌─────────────────────────────────────┐
│ Created once when app starts        │
│ Persists across navigation          │
│ Disposed when app closes             │
│                                      │
│ - LibraryController                  │
│ - FavoritesController                │
│ - SettingsController                 │
└─────────────────────────────────────┘

SCREEN-LEVEL CONTROLLERS (Per-screen)
┌─────────────────────────────────────┐
│ Created when screen opens            │
│ Disposed when screen closes          │
│ Multiple instances can exist         │
│                                      │
│ - PlayerController                   │
└─────────────────────────────────────┘
```

---

## 🚀 Production Components

### Error Handling Flow

```
User Action → Controller Method
       ↓
   try-catch block
       ↓
   Success? → Update state → notifyListeners() → UI updates
       ↓
   Error? → ErrorHandler.handleError(error, stackTrace)
       ↓
   ├─ Log to console (development)
   ├─ Log to Crashlytics (production)
   └─ Show SnackBar to user (user-friendly message)
```

### Logging Levels

```
AppLogger.d()  →  DEBUG    →  Development only
AppLogger.i()  →  INFO     →  Important events
AppLogger.w()  →  WARNING  →  Potential issues
AppLogger.e()  →  ERROR    →  Actual errors
```

### Data Persistence Flow

```
User Action (e.g., toggle favorite)
       ↓
FavoritesController.toggleFavorite()
       ↓
FavoritesRepository.addFavorite() / removeFavorite()
       ↓
Hive Box.put() / Box.delete()
       ↓
Data written to device storage
       ↓
Controller reloads data
       ↓
notifyListeners()
       ↓
UI updates (heart icon fills/empties)
```

---

## 📈 Performance Optimizations

### Lazy Loading Strategy

```
HomeScreen loads
   ↓
Show shimmer loading states
   ↓
LibraryController.load()
   ↓
VideoScanner.scanForVideos()
   ├─ Scan in background isolate
   ├─ Return cached if available
   └─ Debounce rapid scans
   ↓
Group by folders
   ↓
Display incrementally (not all at once)
```

### Caching Strategy

```
┌─────────────────────────────────────┐
│ VideoScanner Cache                  │
│ - Folders list cached for 5 seconds│
│ - Prevents redundant file scans     │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Thumbnail Cache (Future)            │
│ - Generate once, cache on disk      │
│ - Load from cache on subsequent     │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Watch History                       │
│ - Save every 5 seconds (debounced)  │
│ - Quick resume on video open        │
└─────────────────────────────────────┘
```

---

## 🧪 Testing Strategy

```
Unit Tests (Controllers, Repositories)
   ├─ Mock dependencies (VideoScanner, Hive, etc.)
   ├─ Test business logic in isolation
   └─ Target: 80% coverage

Widget Tests (Screens, Widgets)
   ├─ Test UI rendering
   ├─ Test user interactions
   └─ Target: 60% coverage

Integration Tests (Full flows)
   ├─ Test end-to-end scenarios
   ├─ Test with real dependencies
   └─ Target: Critical paths covered
```

---

## 📱 Platform-Specific Code

```
Android
   ├─ Permission handling (Storage, Audio)
   ├─ Background playback service
   └─ PiP mode support

iOS
   ├─ Permission handling (Photos, MediaLibrary)
   ├─ AVPlayer integration
   └─ PiP mode support

Shared (Flutter)
   ├─ UI components
   ├─ Business logic (controllers)
   └─ Data persistence
```

---

## 🔮 Future Architecture Enhancements

### Phase 1 (Current)
- ✅ Clean architecture with controllers
- ✅ Provider state management
- ✅ Repository pattern (PlayerRepository)
- ⏳ Data persistence (Hive + SharedPreferences)

### Phase 2 (Next)
- [ ] Full repository pattern for all features
- [ ] Use cases layer (domain logic)
- [ ] Dependency injection (get_it)
- [ ] Stream-based architecture (BLoC pattern as alternative)

### Phase 3 (Future)
- [ ] Offline-first architecture
- [ ] Sync engine for cloud backup
- [ ] Event sourcing for user actions
- [ ] GraphQL client for backend API

---

## 📚 Resources

- **Architecture Guide:** See `PRODUCTION_ROADMAP.md`
- **Quick Start:** See `QUICK_START.md`
- **Code Style:** Follow `analysis_options.yaml`
- **Contributing:** See `CONTRIBUTING.md` (to be created)

---

**Architecture Questions?** Create an issue or check the documentation.
