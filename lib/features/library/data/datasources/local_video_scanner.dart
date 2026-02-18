import 'dart:io';
import '../../../../../core/services/logger_service.dart';
import '../../../../../core/services/persistent_cache_service.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import 'helpers/scan_orchestrator.dart';
import 'helpers/file_scanner_helper.dart';
import 'helpers/demo_data_helper.dart';

/// Main video scanner with caching
class VideoScanner {
  static final VideoScanner _instance = VideoScanner._internal();
  factory VideoScanner() => _instance;
  VideoScanner._internal();

  List<VideoFolder>? _cachedFolders;
  DateTime? _lastScanTime;
  bool _isScanning = false;
  static const _minScanInterval = Duration(seconds: 5);

  Future<List<VideoFolder>> scanForVideos({
    bool forceRefresh = false,
    Function(double progress, String status)? onProgress,
  }) async {
    // Check caches
    if (!forceRefresh) {
      final cached = await _checkPersistentCache();
      if (cached != null) return cached;
      if (_checkMemoryCache()) return _cachedFolders!;
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

  void clearCache() {
    _cachedFolders = null;
    _lastScanTime = null;
    AppLogger.i('Cache cleared');
  }
}
