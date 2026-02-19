import 'dart:async';
import 'dart:io';
import '../../../../../core/services/logger_service.dart';
import '../../../../../core/services/persistent_cache_service.dart';
import '../../../../../core/utils/debouncer.dart';
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

  // Debouncer for scan requests (prevent rapid re-scans)
  final Debouncer _scanDebouncer = Debouncer(delay: Duration(milliseconds: 500));
  
  // Cooldown manager for refreshes (max 1 refresh per 3 seconds)
  final CooldownManager _refreshCooldown = CooldownManager(cooldown: Duration(seconds: 3));

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

  /// Get current cache stats for debugging
  Map<String, dynamic> get cacheStats => {
    'cachedFolders': _cachedFolders?.length ?? 0,
    'totalVideos': _countVideos(_cachedFolders ?? []),
    'lastScanTime': _lastScanTime?.toIso8601String(),
    'isScanning': _isScanning,
  };

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
        
        // Mark cooldown as complete
        _refreshCooldown.forceExecute(() {});
        
        onProgress?.call(
            1.0, 'Complete! Found ${_countVideos(folders)} videos');

        // Start watching for real-time updates
        if (enableWatching) {
          await startWatching();
        }
      }

      return folders.isNotEmpty ? folders : (_cachedFolders ?? []);
    } catch (e, stack) {
      AppLogger.e('Error scanning videos: $e', e, stack);
      return _cachedFolders ?? [];
    } finally {
      _isScanning = false;
    }
  }

  Future<List<VideoFolder>?> _checkPersistentCache() async {
    // Check if cache exists (don't validate files - too slow!)
    final cached = await PersistentCacheService.loadFromCache();
    if (cached != null && cached.isNotEmpty) {
      _cachedFolders = cached;
      _lastScanTime = await PersistentCacheService.getLastCacheTimestamp();
      AppLogger.i('⚡ Loaded ${cached.length} folders from cache');
      return cached;
    }
    return null;
  }

  /// Get smart cache expiration based on library size
  Future<Duration> _getSmartCacheExpiration() async {
    final cached = await PersistentCacheService.loadFromCache();
    if (cached == null) return Duration.zero;
    
    final totalVideos = _countVideos(cached);
    
    // Small library (< 100 videos) - shorter cache (30 min)
    // Medium library (100-500 videos) - medium cache (2 hours)
    // Large library (> 500 videos) - longer cache (24 hours)
    if (totalVideos < 100) {
      return Duration(minutes: 30);
    } else if (totalVideos < 500) {
      return Duration(hours: 2);
    } else {
      return Duration(hours: 24);
    }
  }

  /// Validate cache by checking if key files still exist
  Future<bool> _validateCache(List<VideoFolder> folders) async {
    if (folders.isEmpty) return false;
    
    // Check first folder's first video as a sample
    for (final folder in folders.take(3)) {
      if (folder.videos.isNotEmpty) {
        final sampleVideo = folder.videos.first;
        final file = File(sampleVideo.path);
        if (!await file.exists()) {
          AppLogger.w('Cache validation failed: ${sampleVideo.path} not found');
          return false;
        }
      }
    }
    
    AppLogger.i('Cache validation passed');
    return true;
  }

  bool _checkMemoryCache() {
    // Memory cache is ALWAYS valid during app lifetime
    // Only invalidate if explicitly cleared or app restarts
    if (_cachedFolders == null || _cachedFolders!.isEmpty) return false;
    
    AppLogger.i('Memory cache hit: ${_cachedFolders!.length} folders');
    return true;
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

    // Listen to changes with debouncing
    _addedSub = _watcher.onVideoAdded
        .listen(_debouncedHandleVideoAdded);
    _removedSub = _watcher.onVideoRemoved
        .listen(_debouncedHandleVideoRemoved);
    _modifiedSub = _watcher.onVideoModified
        .listen(_debouncedHandleVideoModified);

    _isWatching = true;
    AppLogger.i('Directory watching started');
  }

  /// Stop watching directories
  Future<void> stopWatching() async {
    if (!_isWatching) return;

    AppLogger.i('Stopping directory watching...');

    // Cancel all subscriptions
    await _addedSub?.cancel();
    await _removedSub?.cancel();
    await _modifiedSub?.cancel();
    
    // Clear references
    _addedSub = null;
    _removedSub = null;
    _modifiedSub = null;
    
    await _watcher.stopWatching();

    _isWatching = false;
    AppLogger.i('Directory watching stopped');
  }

  // Debounced handlers to prevent rapid UI updates
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
    _batchUpdateTimer = Timer(Duration(milliseconds: 500), _processBatchUpdates);
  }

  void _processBatchUpdates() {
    if (_pendingAddedVideos.isEmpty && 
        _pendingRemovedPaths.isEmpty && 
        _pendingModifiedVideos.isEmpty) {
      return;
    }

    AppLogger.i('Processing batch updates: ${_pendingAddedVideos.length} added, '
        '${_pendingRemovedPaths.length} removed, ${_pendingModifiedVideos.length} modified');

    // Process all pending updates
    for (final video in _pendingAddedVideos) {
      _handleVideoAdded(video);
    }
    for (final path in _pendingRemovedPaths) {
      _handleVideoRemoved(path);
    }
    for (final video in _pendingModifiedVideos) {
      _handleVideoModified(video);
    }

    // Clear pending lists
    _pendingAddedVideos.clear();
    _pendingRemovedPaths.clear();
    _pendingModifiedVideos.clear();
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
