# 🎬 MPx Player

A modern, production-ready video player app built with Flutter, featuring clean architecture, state management, and professional code quality.

[![Flutter](https://img.shields.io/badge/Flutter-3.0%2B-blue.svg)](https://flutter.dev/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

---

## 📱 What is MPx Player?

MPx Player is a **feature-rich local video player** for Android and iOS that lets you:

- 📁 Browse your device storage and find all video files automatically
- ⭐ Mark videos as favorites for quick access
- ▶️ Play videos with advanced playback controls
- 🎯 Resume videos where you left off (with watch history)
- 🔍 Search for videos by name or folder
- 🌓 Enjoy modern Material 3 UI with smooth animations

**Built with production-ready practices:**
- ✅ Clean Architecture (Presentation → Controller → Repository → Data)
- ✅ Provider-based state management
- ✅ Repository pattern for data abstraction
- ✅ Comprehensive error handling
- ✅ Data persistence (Hive + SharedPreferences)
- ✅ Testable code with dependency injection

---

## ✨ Features

### 🏠 Library Management
- **Automatic video scanning** across device storage
- **Folder-based organization** (Camera, Downloads, Movies, etc.)
- **List/Grid view toggle** for browsing
- **Pull-to-refresh** to rescan storage
- **Search functionality** to find videos quickly
- **Demo mode** for testing without real videos

### 🎬 Advanced Video Playback
- **Powered by media_kit** (mpv backend) with hardware acceleration
- **Gesture controls:**
  - Horizontal swipe to seek (±10 seconds)
  - Vertical swipe (left) to adjust brightness
  - Vertical swipe (right) to adjust volume
- **Playback controls:**
  - Play/pause with space bar support
  - Seek bar with live position tracking
  - Speed control (0.25x to 2x)
  - Fullscreen mode
- **Subtitle support** with customization
- **Auto-hiding controls** for immersive viewing

### ⭐ Favorites & History
- **Add videos to favorites** with one tap
- **Watch history** tracks your viewing progress
- **Resume playback** where you left off
- **Continue watching** section for unfinished videos
- **Persistent data** across app restarts

### ⚙️ Settings & Customization
- **Playback preferences** (default speed, auto-play)
- **Subtitle settings** (size, color, background)
- **Theme options** (light/dark mode)
- **Storage management** and cache control

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
│  - FavoritesController                     │
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
│  - Hive Database (Favorites, History)     │
│  - SharedPreferences (Settings)           │
└─────────────────────────────────────────────┘
```

**Key Principles:**
- ✅ **Separation of Concerns** - UI, business logic, and data are separated
- ✅ **Dependency Inversion** - High-level modules don't depend on low-level modules
- ✅ **Testability** - Controllers can be tested without UI
- ✅ **Reusability** - Components are modular and reusable

📚 **[Read full architecture documentation →](ARCHITECTURE.md)**

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
   - You can test with demo data if no videos are found

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
- **hive** ^2.2.3 - Fast NoSQL database (favorites, history)
- **hive_flutter** ^1.1.0 - Flutter integration for Hive
- **shared_preferences** ^2.2.3 - Simple key-value storage (settings)

### UI & Design
- **google_fonts** ^6.2.1 - Custom typography
- **flutter_staggered_animations** ^1.1.1 - Smooth animations
- **Material 3** - Modern design system

### Utilities
- **path_provider** ^2.1.2 - Access device directories
- **permission_handler** ^11.3.0 - Storage permissions
- **wakelock_plus** ^1.2.4 - Prevent screen sleep during playback
- **logger** ^2.0.2 - Structured logging

### Development
- **flutter_lints** ^4.0.0 - Code quality rules
- **mockito** ^5.4.4 - Testing framework
- **build_runner** ^2.4.8 - Code generation

---

## 📁 Project Structure

```
lib/
├── core/                               # Shared utilities
│   ├── database/
│   │   ├── models/                     # Hive models
│   │   │   ├── favorite_video.dart
│   │   │   └── watch_history.dart
│   │   ├── repositories/               # Data access layer
│   │   │   ├── favorites_repository.dart
│   │   │   ├── watch_history_repository.dart
│   │   │   └── settings_repository.dart
│   │   └── database_service.dart       # DB initialization
│   ├── errors/
│   │   ├── app_error.dart             # Error types
│   │   └── error_handler.dart         # Global error handling
│   └── services/
│       ├── logger_service.dart        # Logging
│       └── permission_service.dart    # Permission handling
│
├── features/
│   ├── library/                        # Video library feature
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
│   ├── player/                         # Video player feature
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
│   │           ├── gesture_layer.dart
│   │           └── ...
│   │
│   ├── favorites/                      # Favorites feature
│   │   ├── controller/
│   │   │   └── favorites_controller.dart
│   │   └── presentation/
│   │       └── screens/
│   │           └── favorites_screen.dart
│   │
│   └── settings/                       # Settings feature
│       ├── controller/
│       │   └── settings_controller.dart
│       └── presentation/
│           └── screens/
│               └── settings_screen.dart
│
└── main.dart                           # App entry point

test/                                   # Test files
├── unit/                               # Unit tests
├── widget/                             # Widget tests
└── integration/                        # Integration tests
```

---

## 🎯 Current Status

### ✅ Completed Features
- [x] Clean architecture implementation
- [x] Provider-based state management
- [x] Repository pattern for player
- [x] Video scanning and folder organization
- [x] Advanced video playback with gestures
- [x] Fullscreen mode
- [x] Subtitle support
- [x] Modern Material 3 UI
- [x] List/Grid view toggle
- [x] Permission handling

### 🚧 In Progress
- [ ] Data persistence (Hive + SharedPreferences)
- [ ] Favorites functionality with persistence
- [ ] Watch history and resume playback
- [ ] Search implementation
- [ ] Comprehensive testing (60%+ coverage)

### 📋 Planned Features
- [ ] Video thumbnails
- [ ] Sorting and filtering
- [ ] Playlists
- [ ] Picture-in-Picture mode
- [ ] Background audio playback
- [ ] Cloud sync
- [ ] Dark/light theme toggle

📚 **[See complete roadmap →](PRODUCTION_ROADMAP.md)**

---

## 🎓 Documentation

| Document | Description |
|----------|-------------|
| **[PRODUCTION_ROADMAP.md](PRODUCTION_ROADMAP.md)** | Complete 8-week plan to make the app production-ready |
| **[QUICK_START.md](QUICK_START.md)** | 7-day implementation guide for critical features |
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Detailed architecture documentation with diagrams |

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

### Test Structure
```
test/
├── unit/                    # Unit tests (controllers, repositories)
├── widget/                  # Widget tests (UI components)
└── integration/             # End-to-end tests
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

**IPA location:** Open `ios/Runner.xcworkspace` in Xcode to archive

---

## 🎨 Customization

### Changing Theme Colors

Edit `lib/main.dart`:

```dart
theme: ThemeData(
  colorScheme: ColorScheme.fromSeed(
    seedColor: const Color(0xFF6366F1),  // Primary color
    brightness: Brightness.light,
  ),
  useMaterial3: true,
),
```

### Adding Custom Video Sources

The app automatically scans your device storage for videos. To test with demo data:

1. Open the app
2. If no videos found, tap **"Try Demo Mode"**
3. Demo videos will be loaded for testing

### Modifying Scan Directories

Edit `lib/features/library/data/datasources/local_video_scanner.dart`:

```dart
static final List<String> _videoExtensions = [
  '.mp4',
  '.mkv',
  '.avi',
  '.mov',
  // Add more formats here
];
```

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

- **Video won't play:** Check file format is supported (mp4, mkv, avi, mov)
- **No audio:** Ensure device volume is up and not muted
- **Stuttering playback:** Try enabling hardware acceleration in settings

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

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **[media_kit](https://github.com/media-kit/media-kit)** - For the excellent video player
- **[Flutter](https://flutter.dev/)** - For the amazing framework
- **[Hive](https://docs.hivedb.dev/)** - For fast local storage
- **Material Design** - For design inspiration

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/yourusername/mpx-player/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/mpx-player/discussions)
- **Email:** your.email@example.com

---

## 🗺️ Roadmap to Production

We're actively working towards a production-ready release. Here's the plan:

### Phase 1: Foundation (Weeks 1-2) ⚡ Current Phase
- [ ] Implement data persistence (Hive + SharedPreferences)
- [ ] Add comprehensive error handling
- [ ] Setup logging system
- [ ] Fix all linting issues
- [ ] Write initial tests

### Phase 2: Core Features (Weeks 3-4)
- [ ] Implement search functionality
- [ ] Add watch history with resume
- [ ] Generate video thumbnails
- [ ] Sorting and filtering

### Phase 3: Polish (Weeks 5-6)
- [ ] Performance optimization
- [ ] Dark/light theme
- [ ] Advanced playback features (PiP, playlists)
- [ ] UI animations and transitions

### Phase 4: Release (Weeks 7-8)
- [ ] Firebase integration (Analytics, Crashlytics)
- [ ] CI/CD pipeline setup
- [ ] Beta testing
- [ ] Play Store & App Store submission

**Target Release Date:** 8 weeks from now

📚 **[See detailed roadmap →](PRODUCTION_ROADMAP.md)**

---

## ⭐ Star History

If you find this project useful, please consider giving it a star! ⭐

---

<p align="center">
  Made with ❤️ using Flutter
</p>

<p align="center">
  <a href="PRODUCTION_ROADMAP.md">Production Roadmap</a> •
  <a href="QUICK_START.md">Quick Start Guide</a> •
  <a href="ARCHITECTURE.md">Architecture Docs</a>
</p>
