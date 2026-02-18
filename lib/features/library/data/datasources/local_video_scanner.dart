import 'dart:async';
import 'dart:io';
import '../../../../../core/services/logger_service.dart';
import '../../../../../core/services/persistent_cache_service.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import 'helpers/scan_orchestrator.dart';
import 'helpers/file_scanner_helper.dart';
import 'helpers/demo_data_helper.dart';
import 'helpers/directory_watcher_helper.dart';
import 'helpers/directory_discovery_helper.dart';

/// Main video scanner with caching and real-time watching
class VideoScanner {
  static final VideoScanner _instance = VideoScanner._internal();
  factory VideoScanner() => _instance;
  VideoScanner._internal();

  List<VideoFolder>? _cachedFolders;
  DateTime? _lastScanTime;
  bool _isScanning = false;
  bool _isWatching = false;
  static const _minScanInterval = Duration(seconds: 5);

  final DirectoryWatcherHelper _watcher = DirectoryWatcherHelper();
  StreamSubscription<VideoFile>? _addedSub;
  StreamSubscription<String>? _removedSub;
  StreamSubscription<VideoFile>? _modifiedSub;

  /// Stream of videos added in real-time
  Stream<VideoFile> get onVideoAdded => _watcher.onVideoAdded;

  /// Stream of video paths removed in real-time
  Stream<String> get onVideoRemoved => _watcher.onVideoRemoved;

  /// Stream of videos modified in real-time
  Stream<VideoFile> get onVideoModified => _watcher.onVideoModified;

  /// Whether directory watching is active
  bool get isWatching => _isWatching;

  Future<List<VideoFolder>> scanForVideos({
    bool forceRefresh = false,
    bool enableWatching = true,
    Function(double progress, String status)? onProgress,
  }) async {
    // Check caches
    if (!forceRefresh) {
      final cached = await _checkPersistentCache();
      if (cached != null) {
        if (enableWatching) await startWatching();
        return cached;
      }
      if (_checkMemoryCache()) {
        if (enableWatching) await startWatching();
        return _cachedFolders!;
      }
    }

    // Prevent concurrent scans
    if (_isScanning) {
      await _waitForScan();
      return _cachedFolders ?? [];
    }

    _isScanning = true;

    try {
      final folders = await ScanOrchestrator.scan(
        forceRefresh: forceRefresh,
        onProgress: onProgress,
      );

      _cachedFolders = folders.isNotEmpty ? folders : _cachedFolders;
      _lastScanTime = DateTime.now();
      onProgress?.call(
          1.0, 'Scan completed! Found ${_countVideos(folders)} videos');

      // Start watching for real-time updates
      if (enableWatching) {
        await startWatching();
      }

      return folders;
    } catch (e, stack) {
      AppLogger.e('Error scanning videos: $e', e, stack);
      return _cachedFolders ?? [];
    } finally {
      _isScanning = false;
    }
  }

  Future<List<VideoFolder>?> _checkPersistentCache() async {
    if (await PersistentCacheService.isCacheExpired(const Duration(hours: 1))) {
      return null;
    }

    final cached = await PersistentCacheService.loadFromCache();
    if (cached != null && cached.isNotEmpty) {
      _cachedFolders = cached;
      _lastScanTime = await PersistentCacheService.getLastCacheTimestamp();
      return cached;
    }
    return null;
  }

  bool _checkMemoryCache() {
    if (_cachedFolders == null || _lastScanTime == null) return false;
    return DateTime.now().difference(_lastScanTime!) < _minScanInterval;
  }

  Future<void> _waitForScan() async {
    while (_isScanning) {
      await Future.delayed(const Duration(milliseconds: 100));
    }
  }

  int _countVideos(List<VideoFolder> folders) =>
      folders.fold(0, (sum, f) => sum + f.videos.length);

  Future<List<VideoFile>> getVideosInFolder(String folderPath) async {
    // Check cache first
    final cached = _cachedFolders?.firstWhere(
      (f) => f.path == folderPath,
      orElse: () => VideoFolder(path: '', name: '', videos: []),
    );
    if (cached != null && cached.path.isNotEmpty) {
      return cached.videos;
    }

    // Scan specific folder
    final videos = <VideoFile>[];
    final dir = Directory(folderPath);
    if (await dir.exists()) {
      await FileScannerHelper.scanDirectory(dir, videos);
    }
    return videos..sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
  }

  static List<VideoFolder> getDemoData() => DemoDataHelper.getDemoData();

  /// Start watching directories for real-time updates
  Future<void> startWatching() async {
    if (_isWatching) return;

    AppLogger.i('Starting directory watching...');

    // Get directories to watch
    final directories = await DirectoryDiscoveryHelper.getDirectoriesToScan();

    // Setup watchers
    await _watcher.startWatching(directories);

    // Listen to changes
    _addedSub = _watcher.onVideoAdded.listen(_handleVideoAdded);
    _removedSub = _watcher.onVideoRemoved.listen(_handleVideoRemoved);
    _modifiedSub = _watcher.onVideoModified.listen(_handleVideoModified);

    _isWatching = true;
    AppLogger.i('Directory watching started');
  }

  /// Stop watching directories
  Future<void> stopWatching() async {
    if (!_isWatching) return;

    AppLogger.i('Stopping directory watching...');

    await _addedSub?.cancel();
    await _removedSub?.cancel();
    await _modifiedSub?.cancel();
    await _watcher.stopWatching();

    _isWatching = false;
    AppLogger.i('Directory watching stopped');
  }

  /// Handle video added event
  void _handleVideoAdded(VideoFile video) {
    if (_cachedFolders == null) return;

    // Find or create folder
    var folder = _cachedFolders!.firstWhere(
      (f) => f.path == video.folderPath,
      orElse: () {
        final newFolder = VideoFolder(
          path: video.folderPath,
          name: video.folderName,
          videos: [],
        );
        _cachedFolders!.add(newFolder);
        return newFolder;
      },
    );

    // Add video to folder if not already present
    if (!folder.videos.any((v) => v.path == video.path)) {
      folder.videos.add(video);
      AppLogger.i('Added video to cache: ${video.title}');
    }
  }

  /// Handle video removed event
  void _handleVideoRemoved(String filePath) {
    if (_cachedFolders == null) return;

    for (final folder in _cachedFolders!) {
      folder.videos.removeWhere((v) => v.path == filePath);
    }

    // Remove empty folders
    _cachedFolders!.removeWhere((f) => f.videos.isEmpty);

    AppLogger.i('Removed video from cache: ${filePath.split('/').last}');
  }

  /// Handle video modified event
  void _handleVideoModified(VideoFile video) {
    if (_cachedFolders == null) return;

    for (final folder in _cachedFolders!) {
      final index = folder.videos.indexWhere((v) => v.path == video.path);
      if (index != -1) {
        folder.videos[index] = video;
        AppLogger.i('Updated video in cache: ${video.title}');
        break;
      }
    }
  }

  void clearCache() {
    _cachedFolders = null;
    _lastScanTime = null;
    AppLogger.i('Cache cleared');
  }

  /// Dispose resources
  void dispose() {
    stopWatching();
    _watcher.dispose();
  }
}
