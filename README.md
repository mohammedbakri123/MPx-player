# 🎬 MPx Player

A modern, open-source video player app built with Flutter. Features clean architecture, offline-first design, and professional code quality. **No tracking, no analytics, completely offline.**

[![Flutter](https://img.shields.io/badge/Flutter-3.0%2B-blue.svg)](https://flutter.dev/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-22%20Passing-brightgreen.svg)](test/)

---

## 📱 What is MPx Player?

MPx Player is a **privacy-focused local video player** for Android and iOS that lets you:

- 📁 **Browse** your device storage and find all video files automatically
- ⚡ **Lightning fast** scanning with multi-tier caching (1-2 seconds)
- ⭐ **Mark videos as favorites** with persistent storage
- ▶️ **Play videos** with advanced playback controls and gestures
- 🎯 **Resume videos** where you left off (watch history)
- 🌓 **Modern Material 3 UI** with smooth animations
- 🔒 **100% Offline** - no internet, no tracking, no analytics

**Built with production-ready practices:**
- ✅ Clean Architecture (Presentation → Controller → Repository → Data)
- ✅ Provider-based state management
- ✅ Repository pattern for data abstraction
- ✅ Comprehensive error handling
- ✅ Data persistence (SQLite + SharedPreferences)
- ✅ Unit tests (22 passing tests, targeting 60%+ coverage)
- ✅ **Zero Firebase / Zero Analytics / 100% Offline**

---

## ✨ Features

### 🏠 Library Management
- **Automatic video scanning** across device storage (1-2 seconds with cache)
- **Multi-tier caching** (Memory → SQLite → Disk) for instant subsequent loads
- **Folder-based organization** (Camera, Downloads, Movies, etc.)
- **List/Grid view toggle** for browsing
- **Pull-to-refresh** to rescan storage
- **Lazy loading** - folder contents load on-demand
- **Real-time updates** - watches directories for new/deleted videos
- **Search UI** structure ready for implementation

### 🎬 Advanced Video Playback
- **Powered by media_kit** (mpv backend) with hardware acceleration
- **Gesture controls:**
  - Horizontal swipe to seek (±10 seconds)
  - Vertical swipe (left) to adjust brightness
  - Vertical swipe (right) to adjust volume
  - Long press for 2x speed
- **Playback controls:**
  - Play/pause
  - Seek bar with live position tracking
  - Speed control (0.5x to 2x)
  - Fullscreen mode
- **Subtitle support** with customization (size, color, background)
- **Auto-hiding controls** for immersive viewing
- **Watch history** - tracks viewing progress automatically

### ⭐ Favorites & History
- **Add videos to favorites** with one tap (persisted in SQLite)
- **Watch history** tracks your viewing progress automatically
- **Resume playback** where you left off
- **Persistent data** across app restarts

### ⚙️ Settings & Customization
- **Subtitle settings** (size, color, background)
- **Modern Material 3** design
- **Cache management** and storage optimization

---

## 🏗️ Architecture

MPx Player follows **Clean Architecture** principles:

```
┌─────────────────────────────────────────────┐
│  Presentation Layer (UI + Widgets)         │
│  - Screens, Widgets                        │
│  - Uses context.watch<Controller>()        │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│  Controller Layer (Business Logic)         │
│  - LibraryController                       │
│  - PlayerController                        │
│  - ChangeNotifier for reactive updates    │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│  Domain Layer (Interfaces)                 │
│  - Repository interfaces                   │
│  - Entity models                           │
│  - Zero external dependencies              │
└─────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────┐
│  Data Layer (Implementation)               │
│  - MediaKitPlayerRepository                │
│  - VideoScanner                            │
│  - SQLite Database (Favorites, History)   │
│  - SharedPreferences (Settings)           │
└─────────────────────────────────────────────┘
```

**Key Principles:**
- ✅ **Separation of Concerns** - UI, business logic, and data are separated
- ✅ **Dependency Inversion** - High-level modules don't depend on low-level modules
- ✅ **Testability** - Controllers can be tested without UI (22 unit tests)
- ✅ **Reusability** - Components are modular and reusable
- ✅ **Offline-First** - Everything works without internet

📚 **[Read full architecture documentation →](ARCHITECTURE.md)**
📚 **[Read app understanding guide →](APP_UNDERSTANDING_GUIDE.md)**

---

## 🚀 Getting Started

### Prerequisites

- **Flutter SDK** 3.0.0 or higher ([Install Flutter](https://flutter.dev/docs/get-started/install))
- **Android Studio** / **Xcode** (for mobile development)
- **Git** for version control

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/mpx-player.git
   cd mpx-player/mpx
   ```

2. **Install dependencies:**
   ```bash
   flutter pub get
   ```

3. **Run the app:**
   ```bash
   # List available devices
   flutter devices

   # Run on connected device
   flutter run

   # Run on specific device
   flutter run -d <device_id>
   ```

4. **Grant permissions:**
   - On first launch, grant storage permissions to scan videos
   - The app works completely offline

---

## 📦 Tech Stack

### Core Framework
- **Flutter** 3.0+ - Cross-platform UI framework
- **Dart** 3.0+ - Programming language

### Video Playback
- **media_kit** ^1.1.10 - Modern video player with mpv backend
- **media_kit_video** ^1.2.4 - Video rendering widget
- **media_kit_libs_video** ^1.0.4 - Native mpv libraries

### State Management
- **provider** ^6.1.5 - Reactive state management
- **ChangeNotifier** - For controller implementations

### Data Persistence
- **sqflite** ^2.3.0 - SQLite database (favorites, watch history)
- **shared_preferences** ^2.2.2 - Key-value storage (settings)

### Video Scanning
- **photo_manager** ^3.6.4 - Access to device media library (MediaStore API)

### UI & Design
- **google_fonts** ^6.2.1 - Custom typography
- **flutter_staggered_animations** ^1.1.1 - Smooth animations
- **Material 3** - Modern design system

### Utilities
- **path_provider** ^2.1.2 - Access device directories
- **permission_handler** ^11.3.0 - Storage permissions
- **wakelock_plus** ^1.2.4 - Prevent screen sleep during playback

### Development
- **flutter_lints** ^4.0.0 - Code quality rules
- **mockito** ^5.4.4 - Testing framework
- **build_runner** ^2.4.8 - Code generation

---

## 📁 Project Structure

```
lib/
├── core/                               # Shared utilities
│   ├── database/                       # SQLite database
│   ├── services/                       # Logger, permissions
│   ├── utils/                          # Debouncer, LRU cache
│   └── widgets/                        # MainScreen, PermissionWrapper
│
├── features/                           # Each feature is self-contained
│   ├── library/                        # 📁 Video library feature
│   │   ├── controller/
│   │   │   └── library_controller.dart
│   │   ├── data/
│   │   │   ├── datasources/
│   │   │   │   └── local_video_scanner.dart
│   │   │   └── workers/
│   │   │       └── video_metadata_worker.dart
│   │   ├── domain/
│   │   │   └── entities/
│   │   │       ├── video_file.dart
│   │   │       └── video_folder.dart
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   ├── home_screen.dart
│   │   │   │   └── folder_detail_screen.dart
│   │   │   └── widgets/
│   │   └── services/
│   │       ├── thumbnail_generator.dart
│   │       └── persistent_cache_service.dart
│   │
│   ├── player/                         # 🎬 Video player feature
│   │   ├── controller/
│   │   │   ├── player_controller.dart
│   │   │   ├── player_state.dart
│   │   │   └── mixins/
│   │   │       ├── gesture_handler_mixin.dart
│   │   │       ├── playback_control_mixin.dart
│   │   │       └── subtitle_manager_mixin.dart
│   │   ├── data/
│   │   │   └── repositories/
│   │   │       └── media_kit_player_repository.dart
│   │   ├── domain/
│   │   │   └── repositories/
│   │   │       └── player_repository.dart
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   └── video_player_screen.dart
│   │   │   └── widgets/
│   │   │       ├── player_controls.dart
│   │   │       ├── gesture_detector.dart
│   │   │       └── ...
│   │   └── services/
│   │       ├── play_history_service.dart
│   │       └── last_played_service.dart
│   │
│   ├── favorites/                      # ⭐ Favorites feature
│   │   ├── services/
│   │   │   └── favorites_service.dart
│   │   ├── data/
│   │   │   └── repositories/
│   │   │       └── favorites_repository.dart
│   │   └── presentation/
│   │       └── screens/
│   │           └── favorites_screen.dart
│   │
│   ├── settings/                       # ⚙️ Settings feature
│   │   └── services/
│   │       └── subtitle_settings_service.dart
│   │
│   └── splash/                         # 🚀 Splash screen
│
└── main.dart                           # App entry point

test/                                   # Unit tests
├── mocks/                              # Mock files
│   ├── video_scanner_mock.dart
│   └── player_repository_mock.dart
└── unit/
    └── controllers/
        ├── library_controller_test.dart  # 22 tests ✅
        └── player_controller_test.dart   # Comprehensive tests ✅
```

---

## 🎯 Current Status

### ✅ Completed Features
- [x] Clean architecture implementation
- [x] Provider-based state management
- [x] Repository pattern for player
- [x] **Video scanning** with multi-tier caching (1-2 seconds!)
- [x] **Lazy loading** for folder contents
- [x] **Real-time directory watching** for updates
- [x] **Thumbnail generation** and caching
- [x] Advanced video playback with gestures
- [x] Fullscreen mode
- [x] Subtitle support with customization
- [x] Modern Material 3 UI with animations
- [x] List/Grid view toggle
- [x] Permission handling
- [x] **Comprehensive error handling**
- [x] **Structured logging** system
- [x] **Data persistence** (SQLite + SharedPreferences)
- [x] **Favorites** with persistence
- [x] **Watch history** with resume playback
- [x] **Unit tests** (22 passing for LibraryController)
- [x] **Mockito** setup for testing

### 🚧 In Progress
- [ ] Search implementation (UI ready, logic needed)
- [ ] Watch History UI ("Continue watching" section)
- [ ] Sorting and filtering
- [ ] Widget tests for key screens
- [ ] Integration tests

### 📋 Planned Features
- [ ] Video thumbnails (improvements)
- [ ] Dark/light theme toggle
- [ ] Playlists
- [ ] Picture-in-Picture mode
- [ ] Background audio playback
- [ ] Local error logging for bug reports

---

## 🧪 Testing

### Run Tests
```bash
# Run all tests
flutter test

# Run with coverage
flutter test --coverage

# Run specific test file
flutter test test/unit/controllers/library_controller_test.dart
```

### Current Test Coverage
```
✅ LibraryController: 22 tests passing
   - Initial state tests
   - Video loading tests
   - Refresh functionality
   - View mode toggle
   - Lazy loading tests
   - Cache management tests
   - Error handling tests
   - Edge case tests

✅ PlayerController: Test structure complete
   - Video loading tests
   - Playback control tests
   - Volume control tests
   - Stream listener tests
   - Gesture handling tests
   - Position saving tests

🎯 Target: 60%+ overall coverage for production
```

---

## 🏗️ Building for Production

### Android

```bash
# Debug APK
flutter build apk --debug

# Release APK
flutter build apk --release

# Release App Bundle (for Play Store)
flutter build appbundle --release
```

**APK location:** `build/app/outputs/flutter-apk/app-release.apk`

### iOS (macOS only)

```bash
# Debug build
flutter build ios --debug

# Release build
flutter build ios --release
```

---

## 🔒 Privacy & Offline-First

This app is built with **privacy as a core principle**:

- ✅ **No internet required** - Works completely offline
- ✅ **No Firebase** - No analytics, no crash reporting
- ✅ **No tracking** - Zero data collection
- ✅ **Local storage only** - Data stays on your device
- ✅ **Open source** - Code is transparent and auditable

**Reporting bugs:** If you encounter issues, please report them on GitHub with:
- Device info (Android version, device model)
- Steps to reproduce
- Any error logs from the app

---

## 🐛 Troubleshooting

### App Won't Build

```bash
flutter clean
flutter pub get
flutter pub run build_runner build --delete-conflicting-outputs
flutter run
```

### Permission Issues (Android)

Ensure `AndroidManifest.xml` has storage permissions:

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

For Android 13+, use granular permissions:
```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
```

### Video Playback Issues

- **Video won't play:** Check file format is supported (mp4, mkv, avi, mov, webm)
- **No audio:** Ensure device volume is up and not muted
- **Stuttering playback:** Hardware acceleration enabled by default

### Linting Errors

```bash
# Auto-fix linting issues
dart fix --apply

# Check for issues
flutter analyze
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Before submitting:**
- Run `flutter analyze` (should have 0 errors)
- Run `flutter test` (all tests should pass)
- Follow the existing code style
- Update documentation if needed

---

## 🎓 Documentation

| Document | Description |
|----------|-------------|
| **[APP_UNDERSTANDING_GUIDE.md](APP_UNDERSTANDING_GUIDE.md)** | Complete guide to understanding the codebase |
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Detailed architecture with diagrams |
| **[PRODUCTION_ROADMAP.md](PRODUCTION_ROADMAP.md)** | Complete production roadmap |
| **[PRODUCTION_STATUS_REPORT.md](PRODUCTION_STATUS_REPORT.md)** | Current status and progress |

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **[media_kit](https://github.com/media-kit/media-kit)** - For the excellent video player
- **[Flutter](https://flutter.dev/)** - For the amazing framework
- **[SQLite](https://www.sqlite.org/)** - For reliable local storage
- **Material Design** - For design inspiration
- **AI Assistance** - This project was developed with the help of AI tools (LLMs) for code generation, architecture design, testing, and documentation

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/yourusername/mpx-player/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/mpx-player/discussions)

---

## ⭐ Star History

If you find this project useful, please consider giving it a star! ⭐

---

<p align="center">
  Made with ❤️ using Flutter + AI | 100% Offline | Zero Tracking
</p>

<p align="center">
  <a href="APP_UNDERSTANDING_GUIDE.md">Understanding Guide</a> •
  <a href="ARCHITECTURE.md">Architecture Docs</a> •
  <a href="PRODUCTION_ROADMAP.md">Roadmap</a>
</p>
