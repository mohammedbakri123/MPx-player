import 'dart:async';
import 'dart:io';
import '../../../../../core/services/logger_service.dart';
import '../../services/persistent_cache_service.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import '../../../../../../core/database/app_database.dart';
import 'helpers/scan_orchestrator.dart';
import 'helpers/directory_watcher_helper.dart';
import 'helpers/directory_discovery_helper.dart';

/// Video scanner - loads from DB, scans when needed
class VideoScanner {
  static final VideoScanner _instance = VideoScanner._internal();
  factory VideoScanner() => _instance;
  VideoScanner._internal();

  bool _isScanning = false;
  bool _isWatching = false;
  List<VideoFolder>? _folders;

  final DirectoryWatcherHelper _watcher = DirectoryWatcherHelper();
  StreamSubscription<VideoFile>? _addedSub;
  StreamSubscription<String>? _removedSub;

  Stream<VideoFile> get onVideoAdded => _watcher.onVideoAdded;
  Stream<String> get onVideoRemoved => _watcher.onVideoRemoved;
  bool get isWatching => _isWatching;

  /// Scan for videos - loads from DB first, then scans if empty
  Future<List<VideoFolder>> scanForVideos({
    bool forceRefresh = false,
    bool enableWatching = true,
    Function(double progress, String status)? onProgress,
  }) async {
    // Load from DB
    if (!forceRefresh) {
      final cached = await PersistentCacheService.loadFromCache();
      if (cached != null && cached.isNotEmpty) {
        _folders = cached;
        if (enableWatching) await startWatching();
        return cached;
      }
    }

    // Scan if no cached data
    if (_isScanning) {
      await _waitForScan();
      return _folders ?? [];
    }

    _isScanning = true;
    try {
      final folders = await ScanOrchestrator.scan(
        forceRefresh: forceRefresh,
        onProgress: onProgress,
      );

      if (folders.isNotEmpty) {
        _folders = folders;
        await PersistentCacheService.saveToCache(folders);
        onProgress?.call(1.0, 'Found ${folders.length} folders');
      }

      if (enableWatching) await startWatching();
      return _folders ?? [];
    } catch (e) {
      AppLogger.e('Error scanning: $e');
      return _folders ?? [];
    } finally {
      _isScanning = false;
    }
  }

  /// Get videos for a folder from DB
  Future<List<VideoFile>> getVideosInFolder(String folderPath) async {
    // Try DB first
    try {
      final db = AppDatabase();
      final videos = await db.getVideosByFolder(folderPath);
      if (videos.isNotEmpty) return videos;
    } catch (e) {
      AppLogger.e('Error loading from DB: $e');
    }

    // Fallback: scan directory
    final videos = <VideoFile>[];
    final dir = Directory(folderPath);
    if (await dir.exists()) {
      await _scanDirectorySimple(dir, videos);
    }
    return videos..sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
  }

  Future<void> _scanDirectorySimple(
      Directory directory, List<VideoFile> videos) async {
    final extensions = [
      '.mp4',
      '.mkv',
      '.avi',
      '.mov',
      '.wmv',
      '.flv',
      '.webm',
      '.m4v',
      '.3gp'
    ];
    try {
      await for (final entity in directory.list(recursive: false)) {
        if (entity is File) {
          final ext = entity.path.toLowerCase();
          if (extensions.any((e) => ext.endsWith(e))) {
            final stat = await entity.stat();
            if (stat.size > 100 * 1024) {
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
      AppLogger.e('Error scanning: $e');
    }
  }

  Future<void> _waitForScan() async {
    while (_isScanning) {
      await Future.delayed(const Duration(milliseconds: 100));
    }
  }

  /// Start watching for file changes
  Future<void> startWatching() async {
    if (_isWatching) return;
    final directories = await DirectoryDiscoveryHelper.getDirectoriesToScan();
    await _watcher.startWatching(directories);
    _addedSub = _watcher.onVideoAdded.listen((v) {
      AppLogger.i('Video added: ${v.title}');
    });
    _removedSub = _watcher.onVideoRemoved.listen((p) {
      AppLogger.i('Video removed: $p');
    });
    _isWatching = true;
  }

  /// Stop watching
  Future<void> stopWatching() async {
    await _addedSub?.cancel();
    await _removedSub?.cancel();
    await _watcher.stopWatching();
    _isWatching = false;
  }

  /// Clear cache
  Future<void> clearCache() async {
    _folders = null;
    await PersistentCacheService.clearCache();
  }

  /// Get demo data
  static List<VideoFolder> getDemoData() => [];
}
