import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import '../../../features/library/domain/entities/video_file.dart';
import '../../../features/library/domain/entities/video_folder.dart';
import '../../../core/services/logger_service.dart';

/// Multi-tier caching strategy for optimal performance
/// 
/// L1: Memory Cache (LRU, fast access, limited size)
/// L2: SQLite Database (persistent, structured)
/// L3: Disk Cache (file-based, for thumbnails and large data)
class MultiTierCache {
  static final MultiTierCache _instance = MultiTierCache._internal();
  factory MultiTierCache() => _instance;
  MultiTierCache._internal();

  // L1 Cache configuration
  static const int _l1MaxFolders = 50;
  static const int _l1MaxVideosPerFolder = 100;
  
  // L1: Memory cache for folders (LRU)
  final LinkedHashMap<String, VideoFolder> _folderCache = 
      LinkedHashMap<String, VideoFolder>();
  
  // L1: Video metadata cache (LRU)
  final LinkedHashMap<String, VideoFile> _videoCache = 
      LinkedHashMap<String, VideoFile>();
  
  // Cache statistics
  int _cacheHits = 0;
  int _cacheMisses = 0;
  int _l1Hits = 0;
  int _l2Hits = 0;
  int _l3Hits = 0;

  /// Get cache statistics
  Map<String, dynamic> get stats => {
    'l1_folder_cache_size': _folderCache.length,
    'l1_video_cache_size': _videoCache.length,
    'cache_hits': _cacheHits,
    'cache_misses': _cacheMisses,
    'l1_hits': _l1Hits,
    'l2_hits': _l2Hits,
    'l3_hits': _l3Hits,
    'hit_rate': _cacheHits > 0 
        ? ((_cacheHits / (_cacheHits + _cacheMisses)) * 100).toStringAsFixed(2) + '%'
        : '0%',
  };

  // ==================== FOLDER CACHE (L1) ====================

  /// Get folder from L1 cache
  VideoFolder? getFolderFromCache(String folderPath) {
    final folder = _folderCache.remove(folderPath);
    if (folder != null) {
      _folderCache[folderPath] = folder; // Move to end (MRU)
      _l1Hits++;
      _cacheHits++;
      AppLogger.d('L1 cache hit for folder: $folderPath');
      return folder;
    }
    _cacheMisses++;
    return null;
  }

  /// Store folder in L1 cache
  void storeFolderInCache(VideoFolder folder) {
    // Evict oldest if at capacity
    if (_folderCache.length >= _l1MaxFolders) {
      final oldestKey = _folderCache.keys.first;
      _folderCache.remove(oldestKey);
      AppLogger.d('Evicted folder from L1 cache: $oldestKey');
    }

    // Limit videos per folder in cache
    final limitedFolder = VideoFolder(
      path: folder.path,
      name: folder.name,
      videos: folder.videos.take(_l1MaxVideosPerFolder).toList(),
    );

    _folderCache[folder.path] = limitedFolder;
    AppLogger.d('Stored folder in L1 cache: ${folder.path} (${limitedFolder.videos.length} videos)');
  }

  /// Invalidate folder from cache
  void invalidateFolder(String folderPath) {
    _folderCache.remove(folderPath);
    AppLogger.d('Invalidated folder from cache: $folderPath');
  }

  /// Clear all folder caches
  void clearFolderCache() {
    _folderCache.clear();
    AppLogger.i('L1 folder cache cleared');
  }

  // ==================== VIDEO CACHE (L1) ====================

  /// Get video from L1 cache
  VideoFile? getVideoFromCache(String videoPath) {
    final video = _videoCache.remove(videoPath);
    if (video != null) {
      _videoCache[videoPath] = video; // Move to end (MRU)
      _l1Hits++;
      _cacheHits++;
      return video;
    }
    _cacheMisses++;
    return null;
  }

  /// Store video in L1 cache
  void storeVideoInCache(VideoFile video) {
    // Evict oldest if at capacity
    if (_videoCache.length >= _l1MaxFolders * _l1MaxVideosPerFolder) {
      final oldestKey = _videoCache.keys.first;
      _videoCache.remove(oldestKey);
    }

    _videoCache[video.path] = video;
  }

  /// Store multiple videos in cache
  void storeVideosInCache(List<VideoFile> videos) {
    for (final video in videos) {
      storeVideoInCache(video);
    }
  }

  /// Invalidate video from cache
  void invalidateVideo(String videoPath) {
    _videoCache.remove(videoPath);
  }

  /// Clear all video caches
  void clearVideoCache() {
    _videoCache.clear();
    AppLogger.i('L1 video cache cleared');
  }

  // ==================== DATABASE OPERATIONS (L2) ====================

  /// Save folders to database (L2)
  Future<void> saveFoldersToDatabase(List<VideoFolder> folders) async {
    try {
      final db = await _getDatabase();
      final now = DateTime.now().millisecondsSinceEpoch;

      await db.transaction((txn) async {
        final batch = txn.batch();

        // Insert folders
        for (final folder in folders) {
          batch.insert(
            'folders',
            {
              'path': folder.path,
              'name': folder.name,
              'video_count': folder.videoCount,
              'updated_at': now,
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
        }

        await batch.commit(noResult: true);
      });

      AppLogger.i('Saved ${folders.length} folders to database (L2)');
      _l2Hits++;
    } catch (e) {
      AppLogger.e('Error saving folders to database: $e');
    }
  }

  /// Load folders from database (L2)
  Future<List<VideoFolder>> loadFoldersFromDatabase() async {
    try {
      final db = await _getDatabase();
      final folderMaps = await db.query('folders');

      final folders = <VideoFolder>[];
      for (final folderMap in folderMaps) {
        final folderPath = folderMap['path'] as String;
        final videos = await loadVideosByFolder(folderPath);

        folders.add(VideoFolder(
          path: folderPath,
          name: folderMap['name'] as String,
          videos: videos,
        ));
      }

      if (folders.isNotEmpty) {
        AppLogger.i('Loaded ${folders.length} folders from database (L2)');
        _l2Hits++;
        _cacheHits++;
      }

      return folders;
    } catch (e) {
      AppLogger.e('Error loading folders from database: $e');
      _cacheMisses++;
      return [];
    }
  }

  /// Load videos by folder from database
  Future<List<VideoFile>> loadVideosByFolder(String folderPath) async {
    try {
      final db = await _getDatabase();
      final maps = await db.query(
        'videos',
        where: 'folder_path = ?',
        whereArgs: [folderPath],
        orderBy: 'date_added DESC',
        limit: _l1MaxVideosPerFolder, // Limit for performance
      );

      return maps.map((map) => _videoFromMap(map)).toList();
    } catch (e) {
      AppLogger.e('Error loading videos from database: $e');
      return [];
    }
  }

  /// Save videos to database
  Future<void> saveVideosToDatabase(List<VideoFile> videos) async {
    try {
      final db = await _getDatabase();
      final now = DateTime.now().millisecondsSinceEpoch;

      await db.transaction((txn) async {
        final batch = txn.batch();

        for (final video in videos) {
          batch.insert(
            'videos',
            {
              'id': video.id,
              'path': video.path,
              'title': video.title,
              'folder_path': video.folderPath,
              'folder_name': video.folderName,
              'size': video.size,
              'duration': video.duration,
              'date_added': video.dateAdded.millisecondsSinceEpoch,
              'width': video.width,
              'height': video.height,
              'thumbnail_path': video.thumbnailPath,
              'updated_at': now,
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
        }

        await batch.commit(noResult: true);
      });

      AppLogger.i('Inserted ${videos.length} videos into database (L2)');
    } catch (e) {
      AppLogger.e('Error saving videos to database: $e');
    }
  }

  // ==================== DISK CACHE (L3) ====================

  /// Get disk cache directory
  Future<Directory> _getDiskCacheDirectory() async {
    final directory = await getTemporaryDirectory();
    final cacheDir = Directory('${directory.path}/mpx_cache');
    if (!await cacheDir.exists()) {
      await cacheDir.create(recursive: true);
    }
    return cacheDir;
  }

  /// Save data to disk cache
  Future<void> saveToDiskCache(String key, String data) async {
    try {
      final cacheDir = await _getDiskCacheDirectory();
      final file = File('${cacheDir.path}/$key.cache');
      await file.writeAsString(data);
      AppLogger.d('Saved to disk cache (L3): $key');
      _l3Hits++;
    } catch (e) {
      AppLogger.e('Error saving to disk cache: $e');
    }
  }

  /// Load data from disk cache
  Future<String?> loadFromDiskCache(String key) async {
    try {
      final cacheDir = await _getDiskCacheDirectory();
      final file = File('${cacheDir.path}/$key.cache');
      if (await file.exists()) {
        final data = await file.readAsString();
        AppLogger.d('Loaded from disk cache (L3): $key');
        _l3Hits++;
        _cacheHits++;
        return data;
      }
      _cacheMisses++;
      return null;
    } catch (e) {
      AppLogger.e('Error loading from disk cache: $e');
      _cacheMisses++;
      return null;
    }
  }

  /// Clear disk cache
  Future<void> clearDiskCache() async {
    try {
      final cacheDir = await _getDiskCacheDirectory();
      if (await cacheDir.exists()) {
        await cacheDir.delete(recursive: true);
        await cacheDir.create(recursive: true);
        AppLogger.i('Disk cache (L3) cleared');
      }
    } catch (e) {
      AppLogger.e('Error clearing disk cache: $e');
    }
  }

  /// Get disk cache size
  Future<int> getDiskCacheSize() async {
    try {
      final cacheDir = await _getDiskCacheDirectory();
      int totalSize = 0;

      if (await cacheDir.exists()) {
        await for (final entity in cacheDir.list()) {
          if (entity is File) {
            totalSize += await entity.length();
          }
        }
      }

      return totalSize;
    } catch (e) {
      return 0;
    }
  }

  // ==================== DATABASE HELPER ====================

  static Database? _database;

  /// Get database instance
  Future<Database> _getDatabase() async {
    if (_database != null) {
      return _database!;
    }

    final databasesPath = await getDatabasesPath();
    final path = '$databasesPath/mpx_player.db';

    _database = await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        // Create tables if not exist
        await db.execute('''
          CREATE TABLE IF NOT EXISTS folders (
            path TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            video_count INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL
          )
        ''');

        await db.execute('''
          CREATE TABLE IF NOT EXISTS videos (
            id TEXT PRIMARY KEY,
            path TEXT UNIQUE NOT NULL,
            title TEXT NOT NULL,
            folder_path TEXT NOT NULL,
            folder_name TEXT NOT NULL,
            size INTEGER NOT NULL,
            duration INTEGER NOT NULL,
            date_added INTEGER NOT NULL,
            width INTEGER,
            height INTEGER,
            thumbnail_path TEXT,
            updated_at INTEGER NOT NULL
          )
        ''');

        await db.execute(
            'CREATE INDEX IF NOT EXISTS idx_videos_folder ON videos(folder_path)');
        await db.execute(
            'CREATE INDEX IF NOT EXISTS idx_videos_date ON videos(date_added DESC)');

        AppLogger.i('Database tables created');
      },
    );

    return _database!;
  }

  /// Close database connection
  Future<void> closeDatabase() async {
    if (_database != null) {
      await _database!.close();
      _database = null;
      AppLogger.i('Database connection closed');
    }
  }

  // ==================== CACHE MANAGEMENT ====================

  /// Clear all caches
  Future<void> clearAllCaches() async {
    clearFolderCache();
    clearVideoCache();
    await clearDiskCache();
    await closeDatabase();
    AppLogger.i('All caches cleared');
  }

  /// Reset statistics
  void resetStats() {
    _cacheHits = 0;
    _cacheMisses = 0;
    _l1Hits = 0;
    _l2Hits = 0;
    _l3Hits = 0;
  }

  VideoFile _videoFromMap(Map<String, dynamic> map) {
    return VideoFile(
      id: map['id'] as String,
      path: map['path'] as String,
      title: map['title'] as String,
      folderPath: map['folder_path'] as String,
      folderName: map['folder_name'] as String,
      size: map['size'] as int,
      duration: map['duration'] as int,
      dateAdded: DateTime.fromMillisecondsSinceEpoch(map['date_added'] as int),
      width: map['width'] as int?,
      height: map['height'] as int?,
      thumbnailPath: map['thumbnail_path'] as String?,
    );
  }
}
