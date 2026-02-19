import 'dart:async';
import 'dart:io';
import '../../../../../core/services/logger_service.dart';
import '../../../../../core/services/persistent_cache_service.dart';
import '../../../../../core/utils/debouncer.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import 'helpers/scan_orchestrator.dart';
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

  // Debouncer and cooldown
  final Debouncer _scanDebouncer = Debouncer(delay: const Duration(milliseconds: 500));
  final CooldownManager _refreshCooldown = CooldownManager(cooldown: const Duration(seconds: 3));

  final DirectoryWatcherHelper _watcher = DirectoryWatcherHelper();
  StreamSubscription<VideoFile>? _addedSub;
  StreamSubscription<String>? _removedSub;
  StreamSubscription<VideoFile>? _modifiedSub;

  // Streams for real-time updates
  Stream<VideoFile> get onVideoAdded => _watcher.onVideoAdded;
  Stream<String> get onVideoRemoved => _watcher.onVideoRemoved;
  Stream<VideoFile> get onVideoModified => _watcher.onVideoModified;
  bool get isWatching => _isWatching;

  /// Scan for videos with caching
  Future<List<VideoFolder>> scanForVideos({
    bool forceRefresh = false,
    bool enableWatching = true,
    Function(double progress, String status)? onProgress,
  }) async {
    // Check cooldown for refreshes
    if (forceRefresh && _refreshCooldown.isOnCooldown) {
      AppLogger.w('Scan on cooldown, ignoring refresh request');
      return _cachedFolders ?? [];
    }

    // Check memory cache FIRST - instant!
    if (!forceRefresh && _checkMemoryCache()) {
      AppLogger.i('⚡ Using memory cache - instant!');
      if (enableWatching) await startWatching();
      return _cachedFolders!;
    }

    // Check persistent cache - very fast!
    if (!forceRefresh) {
      final cached = await _checkPersistentCache();
      if (cached != null) {
        AppLogger.i('⚡ Using persistent cache - fast!');
        if (enableWatching) await startWatching();
        return cached;
      }
    }

    // Prevent concurrent scans
    if (_isScanning) {
      AppLogger.i('Scan already in progress, waiting...');
      await _waitForScan();
      return _cachedFolders ?? [];
    }

    _isScanning = true;

    try {
      final folders = await ScanOrchestrator.scan(
        forceRefresh: forceRefresh,
        onProgress: onProgress,
      );

      if (folders.isNotEmpty) {
        _cachedFolders = folders;
        _lastScanTime = DateTime.now();
        _refreshCooldown.forceExecute(() {});
        onProgress?.call(1.0, 'Complete! Found ${_countVideos(folders)} videos');

        if (enableWatching) await startWatching();
      }

      return folders.isNotEmpty ? folders : (_cachedFolders ?? []);
    } catch (e, stack) {
      AppLogger.e('Error scanning videos: $e', e, stack);
      return _cachedFolders ?? [];
    } finally {
      _isScanning = false;
    }
  }

  /// Get videos in a specific folder
  Future<List<VideoFile>> getVideosInFolder(String folderPath) async {
    final cached = _cachedFolders?.firstWhere(
      (f) => f.path == folderPath,
      orElse: () => VideoFolder(path: '', name: '', videos: []),
    );
    if (cached != null && cached.path.isNotEmpty) return cached.videos;

    final videos = <VideoFile>[];
    final dir = Directory(folderPath);
    if (await dir.exists()) {
      await _scanDirectorySimple(dir, videos);
    }
    return videos..sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
  }

  /// Simple directory scan for single folder
  Future<void> _scanDirectorySimple(Directory directory, List<VideoFile> videos) async {
    final videoExtensions = ['.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv', '.webm', '.m4v', '.3gp'];
    try {
      await for (final entity in directory.list(recursive: false)) {
        if (entity is File) {
          final ext = entity.path.toLowerCase();
          if (videoExtensions.any((e) => ext.endsWith(e))) {
            final stat = await entity.stat();
            if (stat.size > 100 * 1024) { // Min 100KB
              videos.add(VideoFile(
                id: entity.path.hashCode.toString(),
                path: entity.path,
                title: entity.path.split('/').last,
                folderPath: entity.parent.path,
                folderName: entity.parent.path.split('/').last,
                size: stat.size,
                duration: 0,
                dateAdded: stat.modified,
              ));
            }
          }
        }
      }
    } catch (e) {
      AppLogger.e('Error scanning directory: $e');
    }
  }

  /// Check memory cache
  bool _checkMemoryCache() {
    if (_cachedFolders == null || _cachedFolders!.isEmpty) return false;
    AppLogger.i('Memory cache hit: ${_cachedFolders!.length} folders');
    return true;
  }

  /// Check persistent cache
  Future<List<VideoFolder>?> _checkPersistentCache() async {
    final cached = await PersistentCacheService.loadFromCache();
    if (cached != null && cached.isNotEmpty) {
      _cachedFolders = cached;
      _lastScanTime = await PersistentCacheService.getLastCacheTimestamp();
      AppLogger.i('⚡ Loaded ${cached.length} folders from cache');
      return cached;
    }
    return null;
  }

  Future<void> _waitForScan() async {
    while (_isScanning) {
      await Future.delayed(const Duration(milliseconds: 100));
    }
  }

  int _countVideos(List<VideoFolder> folders) =>
      folders.fold(0, (sum, f) => sum + f.videos.length);

  /// Start watching directories for real-time updates
  Future<void> startWatching() async {
    if (_isWatching) return;

    AppLogger.i('Starting directory watching...');
    final directories = await DirectoryDiscoveryHelper.getDirectoriesToScan();
    await _watcher.startWatching(directories);

    _addedSub = _watcher.onVideoAdded.listen(_debouncedHandleVideoAdded);
    _removedSub = _watcher.onVideoRemoved.listen(_debouncedHandleVideoRemoved);
    _modifiedSub = _watcher.onVideoModified.listen(_debouncedHandleVideoModified);

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
    _addedSub = null;
    _removedSub = null;
    _modifiedSub = null;
    await _watcher.stopWatching();
    _isWatching = false;
    AppLogger.i('Directory watching stopped');
  }

  // Debounced handlers for batch updates
  final List<VideoFile> _pendingAddedVideos = [];
  final List<String> _pendingRemovedPaths = [];
  final List<VideoFile> _pendingModifiedVideos = [];
  Timer? _batchUpdateTimer;

  void _debouncedHandleVideoAdded(VideoFile video) {
    _pendingAddedVideos.add(video);
    _scheduleBatchUpdate();
  }

  void _debouncedHandleVideoRemoved(String filePath) {
    _pendingRemovedPaths.add(filePath);
    _scheduleBatchUpdate();
  }

  void _debouncedHandleVideoModified(VideoFile video) {
    _pendingModifiedVideos.add(video);
    _scheduleBatchUpdate();
  }

  void _scheduleBatchUpdate() {
    _batchUpdateTimer?.cancel();
    _batchUpdateTimer = Timer(const Duration(milliseconds: 500), _processBatchUpdates);
  }

  void _processBatchUpdates() {
    if (_pendingAddedVideos.isEmpty && _pendingRemovedPaths.isEmpty && _pendingModifiedVideos.isEmpty) {
      return;
    }

    AppLogger.i('Processing batch updates: ${_pendingAddedVideos.length} added, '
        '${_pendingRemovedPaths.length} removed, ${_pendingModifiedVideos.length} modified');

    for (final video in _pendingAddedVideos) _handleVideoAdded(video);
    for (final path in _pendingRemovedPaths) _handleVideoRemoved(path);
    for (final video in _pendingModifiedVideos) _handleVideoModified(video);

    _pendingAddedVideos.clear();
    _pendingRemovedPaths.clear();
    _pendingModifiedVideos.clear();
  }

  void _handleVideoAdded(VideoFile video) {
    if (_cachedFolders == null) return;

    var folder = _cachedFolders!.firstWhere(
      (f) => f.path == video.folderPath,
      orElse: () {
        final newFolder = VideoFolder(path: video.folderPath, name: video.folderName, videos: []);
        _cachedFolders!.add(newFolder);
        return newFolder;
      },
    );

    if (!folder.videos.any((v) => v.path == video.path)) {
      folder.videos.add(video);
      AppLogger.i('Added video to cache: ${video.title}');
    }
  }

  void _handleVideoRemoved(String filePath) {
    if (_cachedFolders == null) return;
    for (final folder in _cachedFolders!) {
      folder.videos.removeWhere((v) => v.path == filePath);
    }
    _cachedFolders!.removeWhere((f) => f.videos.isEmpty);
    AppLogger.i('Removed video from cache: ${filePath.split('/').last}');
  }

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

  /// Get demo data for testing
  static List<VideoFolder> getDemoData() {
    return []; // Simplified - implement if needed
  }

  /// Clear cache
  void clearCache() {
    _batchUpdateTimer?.cancel();
    _pendingAddedVideos.clear();
    _pendingRemovedPaths.clear();
    _pendingModifiedVideos.clear();
    _cachedFolders = null;
    _lastScanTime = null;
    AppLogger.i('Cache cleared');
  }

  /// Dispose resources
  void dispose() {
    AppLogger.i('Disposing VideoScanner...');
    stopWatching();
    _batchUpdateTimer?.cancel();
    _pendingAddedVideos.clear();
    _pendingRemovedPaths.clear();
    _pendingModifiedVideos.clear();
    _watcher.dispose();
    _scanDebouncer.cancel();
    AppLogger.i('VideoScanner disposed');
  }
}
