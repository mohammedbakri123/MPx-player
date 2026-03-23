# 🎬 MPx Player

A modern, open-source video player app built with Flutter. Features clean architecture, offline-first design, and professional code quality. **No tracking, no analytics, completely offline.**

[![Flutter](https://img.shields.io/badge/Flutter-3.0%2B-blue.svg)](https://flutter.dev/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 📱 What is MPx Player?

MPx Player is a **privacy-focused local video player** for Android and iOS that lets you:

- 📁 **Browse** your device storage and find all video files automatically
- ⚡ **Lightning fast** scanning with persistent SQLite indexing
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
- ✅ **Zero Firebase / Zero Analytics / 100% Offline**

---

## ✨ Features

### 🏠 Library Management
- **Persistent library indexing** in SQLite (only scans once, instant subsequently)
- **Automatic filtering** of empty folders (those without videos)
- **Folder-based organization** (Camera, Downloads, Movies, etc.)
- **List/Grid view toggle** for browsing
- **Pull-to-refresh** to rescan storage and rebuild index
- **Instant Search** across the entire indexed library

### 🎬 Advanced Video Playback
- **Powered by flutter_mpv** (mpv backend) with hardware acceleration
- **Gesture controls:**
  - Horizontal swipe to seek
  - Vertical swipe (left) to adjust brightness
  - Vertical swipe (right) to adjust volume
  - Long press for 2x speed
  - Double tap zones for seeking/pause
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
- **Continue watching** section for quick access
- **Persistent data** across app restarts

### ⚙️ Settings & Customization
- **Subtitle settings** (size, color, background)
- **Modern Material 3** design
- **Cache management** for thumbnails

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
│  - FileBrowserController                   │
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
│  - MpvPlayerRepository                     │
│  - DirectoryBrowser (Datasource)           │
│  - SQLite Database (Library, History, Favs)│
│  - SharedPreferences (Settings)           │
└─────────────────────────────────────────────┘
```

📚 **[Read full architecture documentation →](ARCHITECTURE.md)**
📚 **[Read app understanding guide →](APP_UNDERSTANDING_GUIDE.md)**

---

## 🚀 Getting Started

### Prerequisites

- **Flutter SDK** 3.0.0 or higher
- **Android Studio** / **Xcode**
- **Git**

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
   flutter run
   ```

### Contributing

- Read [`CONTRIBUTING.md`](CONTRIBUTING.md) for coding expectations, PR guidance, and release workflow.
- For publish readiness, validate playback, scrolling, and release artifacts before opening a release PR.

---

## 📦 Tech Stack

### Core Framework
- **Flutter** 3.0+ - Cross-platform UI framework
- **Dart** 3.0+ - Programming language

### Video Playback
- **flutter_mpv** - Modern video player with mpv backend
- **flutter_mpv_video** - Video rendering widget

### State Management
- **provider** - Reactive state management

### Data Persistence
- **sqflite** - SQLite database (indexing, favorites, watch history)
- **shared_preferences** - Key-value storage (settings)

### UI & Design
- **google_fonts** - Custom typography
- **flutter_staggered_animations** - Smooth animations
- **Material 3** - Modern design system

---

## 📁 Project Structure

```
lib/
├── core/                               # Shared utilities
│   ├── database/                       # SQLite database
│   ├── services/                       # Logger, permissions
│   └── widgets/                        # Common UI components
│
├── features/                           # Each feature is self-contained
│   ├── library/                        # 📁 Video library feature
│   │   ├── controller/
│   │   │   └── file_browser_controller.dart
│   │   ├── data/
│   │   │   └── datasources/
│   │   │       └── directory_browser.dart
│   │   ├── domain/
│   │   │   └── entities/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   └── widgets/
│   │   └── services/
│   │       └── library_index_service.dart
│   │
│   ├── player/                         # 🎬 Video player feature
│   │   ├── controller/
│   │   │   ├── player_controller.dart
│   │   │   ├── player_state.dart
│   │   │   └── mixins/
│   │   ├── data/
│   │   │   └── repositories/
│   │   ├── domain/
│   │   │   └── repositories/
│   │   └── presentation/
│   │
│   ├── favorites/                      # ⭐ Favorites feature
│   │   ├── services/
│   │   └── presentation/
│   │
│   ├── settings/                       # ⚙️ Settings feature
│   │
│   └── splash/                         # 🚀 Splash screen
│
└── main.dart                           # App entry point
```

---

## 🎯 Current Status

### ✅ Completed Features
- [x] Clean architecture & Provider state management
- [x] **Persistent SQLite indexing** (Instant library loads)
- [x] **Empty folder filtering**
- [x] **Instant Search** implementation
- [x] Advanced video playback with gestures
- [x] Subtitle support with customization
- [x] Watch history with resume playback
- [x] Modern Material 3 UI with animations
- [x] 100% Offline / Zero Tracking

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ using Flutter | 100% Offline | Zero Tracking
</p>
