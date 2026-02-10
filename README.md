# MPx Player - Flutter Video Player

A modern, feature-rich video player built with Flutter and media_kit (mpv backend).

## Features

- **Home Screen**: Browse storage directories (Camera, Downloads, Movies, etc.)
- **Favorites Screen**: View favorite videos with thumbnail previews
- **Video Player**: Full-featured player powered by mpv with:
  - Hardware acceleration
  - Gesture controls
  - Seek controls (+/- 10 seconds)
  - Play/Pause functionality
  - Progress tracking
- **Settings Screen**: User preferences and app settings

## Architecture

This Flutter app converts the React app from `Media-Player-X/` with the following mapping:

| React Component | Flutter Equivalent |
|----------------|-------------------|
| Home.tsx | `screens/home_screen.dart` |
| Favorites.tsx | `screens/favorites_screen.dart` |
| Settings.tsx | `screens/settings_screen.dart` |
| App.tsx (Router) | `main.dart` (BottomNavigationBar) |
| Video Player | `screens/video_player_screen.dart` (media_kit) |

## Tech Stack

- **Flutter** - UI Framework
- **media_kit** - Video player with mpv backend
- **google_fonts** - Typography
- **flutter_staggered_animations** - Smooth list animations
- **go_router** - Navigation

## Getting Started

### Prerequisites

- Flutter SDK (3.0.0 or higher)
- Android Studio / Xcode (for emulators)
- Git

### Installation

1. **Navigate to the Flutter project:**
   ```bash
   cd mpx
   ```

2. **Install dependencies:**
   ```bash
   flutter pub get
   ```

3. **Run the app:**
   ```bash
   # For Android
   flutter run
   
   # For specific device
   flutter devices  # List available devices
   flutter run -d <device_id>
   ```

### Building for Production

```bash
# Android APK
flutter build apk --release

# Android App Bundle
flutter build appbundle --release

# iOS (requires macOS and Xcode)
flutter build ios --release
```

## Project Structure

```
lib/
├── main.dart              # App entry point & navigation
├── screens/
│   ├── home_screen.dart       # Folders list
│   ├── favorites_screen.dart  # Favorite videos
│   ├── settings_screen.dart   # App settings
│   └── video_player_screen.dart  # Video player with media_kit
assets/
└── thumbnails/            # Video thumbnail images
```

## Key Features Implemented

### 1. Home Screen
- List of storage directories
- Animated folder cards
- View toggle (List/Grid)
- Floating action button

### 2. Favorites Screen
- Video cards with thumbnails
- Resolution and duration badges
- Play button overlay
- Tap to play video

### 3. Video Player (media_kit)
- Full-screen video playback
- mpv-powered backend
- Custom controls overlay
- Seek forward/backward (10s)
- Progress slider
- Play/Pause toggle
- Auto-hiding controls

### 4. Settings Screen
- User profile section
- General settings (Account, Privacy, Device)
- Preferences toggles (Wi-Fi download, Dark mode)
- Logout button

## Customization

### Changing Video Sources

Edit the `favorites` list in `lib/screens/favorites_screen.dart`:

```dart
final List<VideoItem> favorites = [
  VideoItem(
    id: '1',
    title: 'Your Video Name',
    duration: '10:30',
    size: '100 MB',
    date: 'Today',
    thumbnail: 'assets/thumbnails/your_thumb.jpg',
    resolution: '1080P',
    videoUrl: 'https://your-video-url.com/video.mp4',
  ),
];
```

### Theming

Edit the theme in `lib/main.dart`:

```dart
theme: ThemeData(
  colorScheme: ColorScheme.fromSeed(
    seedColor: const Color(0xFF6366F1),  // Change primary color
    brightness: Brightness.light,
  ),
),
```

## Troubleshooting

### LSP Errors in IDE

The LSP errors shown during development are expected before running `flutter pub get`. To resolve:

```bash
flutter pub get
```

Then restart your IDE or run:

```bash
flutter clean
flutter pub get
```

### Video Not Playing

- Ensure you have a valid video URL
- Check internet connectivity for network videos
- For local videos, add them to `assets/videos/` and update `pubspec.yaml`

### Android Build Issues

```bash
flutter clean
flutter pub get
cd android
./gradlew clean
cd ..
flutter build apk
```

## Dependencies

See `pubspec.yaml` for full list. Key dependencies:

- `media_kit: ^1.1.10` - Video player core
- `media_kit_video: ^1.2.4` - Video widget
- `media_kit_libs_video: ^1.0.4` - Native libraries
- `google_fonts: ^6.2.1` - Custom fonts
- `flutter_staggered_animations: ^1.1.1` - Animations

## License

This project is a conversion of the original React app for educational purposes.

## Next Steps

To add more features:
- [ ] Local video file picker
- [ ] Playlist management
- [ ] Subtitle support
- [ ] Audio track switching
- [ ] Playback speed control
- [ ] Picture-in-picture mode
- [ ] Chromecast support

## Support

For issues related to:
- **media_kit**: https://github.com/media-kit/media-kit
- **Flutter**: https://flutter.dev/docs
