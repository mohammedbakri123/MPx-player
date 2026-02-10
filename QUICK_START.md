# 🚀 Quick Start: First Week Implementation

This guide helps you implement the **most critical production features** in your first week.

---

## Day 1: Data Persistence Setup (Hive + SharedPreferences)

### Step 1: Add Dependencies

```bash
flutter pub add hive hive_flutter shared_preferences
flutter pub add --dev hive_generator build_runner
```

### Step 2: Initialize Hive

**Update `lib/main.dart`:**
```dart
import 'package:hive_flutter/hive_flutter.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Hive
  await Hive.initFlutter();
  
  // Register adapters (will create these next)
  Hive.registerAdapter(FavoriteVideoAdapter());
  Hive.registerAdapter(WatchHistoryAdapter());
  
  MediaKit.ensureInitialized();
  
  runApp(const MPxPlayer());
}
```

### Step 3: Create Hive Models

**Create `lib/core/database/models/favorite_video.dart`:**
```dart
import 'package:hive/hive.dart';

part 'favorite_video.g.dart';

@HiveType(typeId: 0)
class FavoriteVideo extends HiveObject {
  @HiveField(0)
  final String path;

  @HiveField(1)
  final String title;

  @HiveField(2)
  final String folderPath;

  @HiveField(3)
  final DateTime addedAt;

  FavoriteVideo({
    required this.path,
    required this.title,
    required this.folderPath,
    required this.addedAt,
  });

  factory FavoriteVideo.fromVideoFile(VideoFile video) {
    return FavoriteVideo(
      path: video.path,
      title: video.title,
      folderPath: video.path.substring(0, video.path.lastIndexOf('/')),
      addedAt: DateTime.now(),
    );
  }
}
```

**Create `lib/core/database/models/watch_history.dart`:**
```dart
import 'package:hive/hive.dart';

part 'watch_history.g.dart';

@HiveType(typeId: 1)
class WatchHistory extends HiveObject {
  @HiveField(0)
  final String videoPath;

  @HiveField(1)
  final String videoTitle;

  @HiveField(2)
  final int lastPosition; // milliseconds

  @HiveField(3)
  final int duration; // milliseconds

  @HiveField(4)
  final DateTime lastWatched;

  WatchHistory({
    required this.videoPath,
    required this.videoTitle,
    required this.lastPosition,
    required this.duration,
    required this.lastWatched,
  });

  double get progress => duration > 0 ? lastPosition / duration : 0.0;
  bool get isCompleted => progress > 0.9; // 90% watched = completed
}
```

### Step 4: Generate Code

```bash
flutter pub run build_runner build --delete-conflicting-outputs
```

### Step 5: Create Repositories

**Create `lib/core/database/repositories/favorites_repository.dart`:**
```dart
import 'package:hive/hive.dart';
import '../models/favorite_video.dart';
import '../../../features/library/domain/entities/video_file.dart';

class FavoritesRepository {
  static const String _boxName = 'favorites';
  Box<FavoriteVideo>? _box;

  Future<void> init() async {
    _box = await Hive.openBox<FavoriteVideo>(_boxName);
  }

  Future<void> addFavorite(VideoFile video) async {
    final favorite = FavoriteVideo.fromVideoFile(video);
    await _box?.put(video.path, favorite);
  }

  Future<void> removeFavorite(String path) async {
    await _box?.delete(path);
  }

  bool isFavorite(String path) {
    return _box?.containsKey(path) ?? false;
  }

  List<FavoriteVideo> getAllFavorites() {
    return _box?.values.toList() ?? [];
  }

  Future<void> clearAll() async {
    await _box?.clear();
  }

  Stream<BoxEvent> watch() {
    return _box?.watch() ?? Stream.empty();
  }
}
```

**Create `lib/core/database/repositories/watch_history_repository.dart`:**
```dart
import 'package:hive/hive.dart';
import '../models/watch_history.dart';

class WatchHistoryRepository {
  static const String _boxName = 'watch_history';
  static const int _maxHistoryItems = 100; // Keep last 100 videos
  Box<WatchHistory>? _box;

  Future<void> init() async {
    _box = await Hive.openBox<WatchHistory>(_boxName);
  }

  Future<void> saveProgress({
    required String videoPath,
    required String videoTitle,
    required int position,
    required int duration,
  }) async {
    final history = WatchHistory(
      videoPath: videoPath,
      videoTitle: videoTitle,
      lastPosition: position,
      duration: duration,
      lastWatched: DateTime.now(),
    );

    await _box?.put(videoPath, history);
    await _cleanupOldHistory();
  }

  WatchHistory? getHistory(String videoPath) {
    return _box?.get(videoPath);
  }

  List<WatchHistory> getRecentHistory({int limit = 20}) {
    final allHistory = _box?.values.toList() ?? [];
    allHistory.sort((a, b) => b.lastWatched.compareTo(a.lastWatched));
    return allHistory.take(limit).toList();
  }

  List<WatchHistory> getContinueWatching() {
    final allHistory = _box?.values.toList() ?? [];
    return allHistory
        .where((h) => !h.isCompleted && h.progress > 0.05) // Started but not finished
        .toList()
      ..sort((a, b) => b.lastWatched.compareTo(a.lastWatched));
  }

  Future<void> clearHistory() async {
    await _box?.clear();
  }

  Future<void> _cleanupOldHistory() async {
    final allHistory = _box?.values.toList() ?? [];
    if (allHistory.length > _maxHistoryItems) {
      allHistory.sort((a, b) => b.lastWatched.compareTo(a.lastWatched));
      final toRemove = allHistory.skip(_maxHistoryItems);
      for (final item in toRemove) {
        await _box?.delete(item.videoPath);
      }
    }
  }
}
```

**Create `lib/core/database/repositories/settings_repository.dart`:**
```dart
import 'package:shared_preferences/shared_preferences.dart';

class SettingsRepository {
  static const String _keyThemeMode = 'theme_mode';
  static const String _keyDefaultSpeed = 'default_speed';
  static const String _keySubtitleSize = 'subtitle_size';
  static const String _keySubtitleColor = 'subtitle_color';
  static const String _keyAutoPlay = 'auto_play';

  SharedPreferences? _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  // Theme
  Future<void> setThemeMode(String mode) async {
    await _prefs?.setString(_keyThemeMode, mode);
  }

  String getThemeMode() {
    return _prefs?.getString(_keyThemeMode) ?? 'system';
  }

  // Playback Speed
  Future<void> setDefaultSpeed(double speed) async {
    await _prefs?.setDouble(_keyDefaultSpeed, speed);
  }

  double getDefaultSpeed() {
    return _prefs?.getDouble(_keyDefaultSpeed) ?? 1.0;
  }

  // Subtitle Size
  Future<void> setSubtitleSize(double size) async {
    await _prefs?.setDouble(_keySubtitleSize, size);
  }

  double getSubtitleSize() {
    return _prefs?.getDouble(_keySubtitleSize) ?? 24.0;
  }

  // Auto Play
  Future<void> setAutoPlay(bool enabled) async {
    await _prefs?.setBool(_keyAutoPlay, enabled);
  }

  bool getAutoPlay() {
    return _prefs?.getBool(_keyAutoPlay) ?? true;
  }

  // Clear all settings
  Future<void> clearAll() async {
    await _prefs?.clear();
  }
}
```

### Step 6: Initialize Repositories

**Create `lib/core/database/database_service.dart`:**
```dart
import 'favorites_repository.dart';
import 'watch_history_repository.dart';
import 'settings_repository.dart';

class DatabaseService {
  static final DatabaseService instance = DatabaseService._internal();
  factory DatabaseService() => instance;
  DatabaseService._internal();

  final FavoritesRepository favorites = FavoritesRepository();
  final WatchHistoryRepository watchHistory = WatchHistoryRepository();
  final SettingsRepository settings = SettingsRepository();

  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;

    await Future.wait([
      favorites.init(),
      watchHistory.init(),
      settings.init(),
    ]);

    _initialized = true;
  }
}
```

**Update `lib/main.dart`:**
```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Hive
  await Hive.initFlutter();
  Hive.registerAdapter(FavoriteVideoAdapter());
  Hive.registerAdapter(WatchHistoryAdapter());
  
  // Initialize database
  await DatabaseService.instance.init();
  
  MediaKit.ensureInitialized();
  
  runApp(const MPxPlayer());
}
```

---

## Day 2: Update Controllers to Use Persistence

### Update FavoritesController

**Create `lib/features/favorites/controller/favorites_controller.dart`:**
```dart
import 'package:flutter/foundation.dart';
import '../../../core/database/database_service.dart';
import '../../../core/database/models/favorite_video.dart';
import '../../library/domain/entities/video_file.dart';

class FavoritesController extends ChangeNotifier {
  final _favoritesRepo = DatabaseService.instance.favorites;

  List<FavoriteVideo> _favorites = [];
  bool _isLoading = true;

  List<FavoriteVideo> get favorites => _favorites;
  bool get isLoading => _isLoading;
  bool get isEmpty => _favorites.isEmpty && !_isLoading;

  FavoritesController() {
    loadFavorites();
  }

  Future<void> loadFavorites() async {
    _isLoading = true;
    notifyListeners();

    try {
      _favorites = _favoritesRepo.getAllFavorites();
      _favorites.sort((a, b) => b.addedAt.compareTo(a.addedAt));
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  bool isFavorite(String videoPath) {
    return _favoritesRepo.isFavorite(videoPath);
  }

  Future<void> toggleFavorite(VideoFile video) async {
    if (isFavorite(video.path)) {
      await _favoritesRepo.removeFavorite(video.path);
    } else {
      await _favoritesRepo.addFavorite(video);
    }
    await loadFavorites();
  }

  Future<void> clearAll() async {
    await _favoritesRepo.clearAll();
    await loadFavorites();
  }
}
```

### Update PlayerController for Watch History

**Update `lib/features/player/controller/player_controller.dart`:**
```dart
import '../../../core/database/database_service.dart';

class PlayerController extends ChangeNotifier {
  // ... existing code ...

  final _watchHistoryRepo = DatabaseService.instance.watchHistory;
  String? _currentVideoPath;
  String? _currentVideoTitle;

  Future<void> loadVideo(String path, {String? title}) async {
    _currentVideoPath = path;
    _currentVideoTitle = title ?? path.split('/').last;
    
    await _repository.load(path);
    
    // Restore last position from watch history
    final history = _watchHistoryRepo.getHistory(path);
    if (history != null && history.lastPosition > 0 && !history.isCompleted) {
      await seek(Duration(milliseconds: history.lastPosition));
    }
  }

  // Save progress every 5 seconds
  Timer? _saveProgressTimer;

  void _setupListeners() {
    // ... existing listeners ...

    // Save progress periodically
    _saveProgressTimer = Timer.periodic(Duration(seconds: 5), (_) {
      _saveProgress();
    });
  }

  Future<void> _saveProgress() async {
    if (_currentVideoPath != null && 
        _currentVideoTitle != null && 
        duration.inMilliseconds > 0) {
      await _watchHistoryRepo.saveProgress(
        videoPath: _currentVideoPath!,
        videoTitle: _currentVideoTitle!,
        position: position.inMilliseconds,
        duration: duration.inMilliseconds,
      );
    }
  }

  @override
  void dispose() {
    _saveProgressTimer?.cancel();
    _saveProgress(); // Save one last time
    WakelockPlus.disable();
    _repository.dispose();
    super.dispose();
  }
}
```

### Provide Controllers in main.dart

**Update `lib/main.dart`:**
```dart
class MPxPlayer extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => LibraryController(VideoScanner()),
        ),
        ChangeNotifierProvider(
          create: (_) => FavoritesController(),
        ),
      ],
      child: MaterialApp(
        // ... rest of your app
      ),
    );
  }
}
```

---

## Day 3: Add Error Handling

### Create Error Types

**Create `lib/core/errors/app_error.dart`:**
```dart
sealed class AppError implements Exception {
  final String message;
  final String? details;
  final StackTrace? stackTrace;

  const AppError(this.message, [this.details, this.stackTrace]);

  @override
  String toString() => '$runtimeType: $message${details != null ? ' - $details' : ''}';
}

class NetworkError extends AppError {
  const NetworkError([String? details, StackTrace? stackTrace])
      : super('Network connection failed', details, stackTrace);
}

class PermissionError extends AppError {
  const PermissionError([String? details, StackTrace? stackTrace])
      : super('Permission denied', details, stackTrace);
}

class VideoLoadError extends AppError {
  const VideoLoadError([String? details, StackTrace? stackTrace])
      : super('Failed to load video', details, stackTrace);
}

class StorageScanError extends AppError {
  const StorageScanError([String? details, StackTrace? stackTrace])
      : super('Failed to scan storage', details, stackTrace);
}

class DatabaseError extends AppError {
  const DatabaseError([String? details, StackTrace? stackTrace])
      : super('Database operation failed', details, stackTrace);
}
```

### Create Error Handler

**Create `lib/core/errors/error_handler.dart`:**
```dart
import 'package:flutter/material.dart';
import 'app_error.dart';

class ErrorHandler {
  static final GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey =
      GlobalKey<ScaffoldMessengerState>();

  static void handleError(Object error, [StackTrace? stackTrace]) {
    debugPrint('❌ Error: $error');
    if (stackTrace != null) {
      debugPrint('Stack trace: $stackTrace');
    }

    // Log to crash reporting in production
    // FirebaseCrashlytics.instance.recordError(error, stackTrace);

    // Show user-friendly message
    String message = 'An unexpected error occurred';
    
    if (error is AppError) {
      message = error.message;
    } else if (error is FormatException) {
      message = 'Invalid data format';
    } else if (error is TypeError) {
      message = 'Data type error';
    }

    showErrorSnackbar(message);
  }

  static void showErrorSnackbar(String message) {
    scaffoldMessengerKey.currentState?.showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red.shade700,
        behavior: SnackBarBehavior.floating,
        action: SnackBarAction(
          label: 'Dismiss',
          textColor: Colors.white,
          onPressed: () {
            scaffoldMessengerKey.currentState?.hideCurrentSnackBar();
          },
        ),
      ),
    );
  }

  static void showSuccessSnackbar(String message) {
    scaffoldMessengerKey.currentState?.showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green.shade700,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}
```

### Update main.dart to use ErrorHandler

```dart
class MPxPlayer extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => LibraryController(VideoScanner())),
        ChangeNotifierProvider(create: (_) => FavoritesController()),
      ],
      child: MaterialApp(
        scaffoldMessengerKey: ErrorHandler.scaffoldMessengerKey, // Add this
        // ... rest
      ),
    );
  }
}
```

---

## Day 4-5: Add Logging & Fix Linting

### Add Logger

```bash
flutter pub add logger
```

**Create `lib/core/services/logger_service.dart`:**
```dart
import 'package:logger/logger.dart';

class AppLogger {
  static final Logger _logger = Logger(
    printer: PrettyPrinter(
      methodCount: 2,
      errorMethodCount: 8,
      lineLength: 120,
      colors: true,
      printEmojis: true,
      printTime: true,
    ),
  );

  static void d(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.d(message, error: error, stackTrace: stackTrace);
  }

  static void i(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.i(message, error: error, stackTrace: stackTrace);
  }

  static void w(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.w(message, error: error, stackTrace: stackTrace);
  }

  static void e(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.e(message, error: error, stackTrace: stackTrace);
  }
}
```

### Replace all print() statements:

```bash
# Find all print statements
grep -r "print(" lib/

# Replace with AppLogger
# print('message') → AppLogger.i('message')
# print('Error: $e') → AppLogger.e('Error', e, stackTrace)
```

### Fix Linting Issues

```bash
# Auto-fix
dart fix --apply

# Check remaining issues
flutter analyze

# Fix deprecated APIs manually
# .withOpacity(0.5) → .withValues(alpha: 0.5)
```

---

## Day 6-7: Testing Setup

### Add Test Dependencies

```bash
flutter pub add --dev mockito build_runner
```

### Write First Test

**Create `test/unit/controllers/library_controller_test.dart`:**
```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:mpx/features/library/controller/library_controller.dart';
import 'package:mpx/features/library/data/datasources/local_video_scanner.dart';
import 'package:mpx/features/library/domain/entities/video_folder.dart';

@GenerateMocks([VideoScanner])
import 'library_controller_test.mocks.dart';

void main() {
  group('LibraryController', () {
    late LibraryController controller;
    late MockVideoScanner mockScanner;

    setUp(() {
      mockScanner = MockVideoScanner();
      controller = LibraryController(mockScanner);
    });

    tearDown(() {
      controller.dispose();
    });

    test('initial state should be loading', () {
      expect(controller.isLoading, true);
      expect(controller.folders, isEmpty);
    });

    test('load() should fetch folders successfully', () async {
      final mockFolders = [
        VideoFolder(name: 'Movies', path: '/storage/movies', videos: []),
      ];

      when(mockScanner.scanForVideos(forceRefresh: false))
          .thenAnswer((_) async => mockFolders);

      await controller.load();

      expect(controller.isLoading, false);
      expect(controller.folders, mockFolders);
      expect(controller.hasError, false);
    });

    test('load() should handle errors gracefully', () async {
      when(mockScanner.scanForVideos(forceRefresh: false))
          .thenThrow(Exception('Scan failed'));

      await controller.load();

      expect(controller.isLoading, false);
      expect(controller.hasError, true);
      expect(controller.errorMessage, isNotNull);
    });
  });
}
```

### Generate Mocks

```bash
flutter pub run build_runner build --delete-conflicting-outputs
```

### Run Tests

```bash
flutter test
```

---

## Summary Checklist

After completing this week, you should have:

- ✅ Data persistence with Hive + SharedPreferences
- ✅ Favorites persisting across app restarts
- ✅ Watch history tracking last position
- ✅ Settings saved (theme, playback speed, etc.)
- ✅ Error handling throughout the app
- ✅ Logging system in place
- ✅ Linting issues fixed
- ✅ First unit tests written
- ✅ Test infrastructure setup

**Next week:** Continue with search, sorting, and more tests!

---

**Need help?** Refer to `PRODUCTION_ROADMAP.md` for the complete plan.
