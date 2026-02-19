import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../features/library/domain/entities/video_folder.dart';
import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';

/// Persistent cache service with SQLite database support
/// Falls back to SharedPreferences if database fails
class PersistentCacheService {
  static const String _cacheKey = 'video_folders_cache';
  static const String _timestampKey = 'video_folders_timestamp';
  static const String _fileMetadataKey = 'file_metadata_cache';
  static const String _useDatabaseKey = 'cache_use_database';

  static bool _databaseAvailable = true;

  /// Check if database is available
  static Future<bool> _isDatabaseAvailable() async {
    if (!_databaseAvailable) return false;

    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool(_useDatabaseKey) ?? true;
    } catch (e) {
      return false;
    }
  }

  /// Disable database and fall back to SharedPreferences
  static Future<void> _disableDatabase() async {
    _databaseAvailable = false;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_useDatabaseKey, false);
    AppLogger.w('Database disabled, falling back to SharedPreferences');
  }

  // Save video folders to persistent cache
  static Future<void> saveToCache(List<VideoFolder> folders) async {
    // Try database first
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        await db.insertFolders(folders);

        // Insert videos for each folder
        for (final folder in folders) {
          if (folder.videos.isNotEmpty) {
            await db.insertVideos(folder.videos);
          }
        }

        AppLogger.i('Saved ${folders.length} folders to database');
        return;
      } catch (e) {
        AppLogger.e('Failed to save to database: $e');
        await _disableDatabase();
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    await _saveToSharedPreferences(folders);
  }

  /// Save to SharedPreferences (fallback)
  static Future<void> _saveToSharedPreferences(
      List<VideoFolder> folders) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonList = folders.map((folder) => folder.toJson()).toList();
    final jsonString = jsonEncode(jsonList);

    await prefs.setString(_cacheKey, jsonString);
    await prefs.setInt(_timestampKey, DateTime.now().millisecondsSinceEpoch);
    AppLogger.i('Saved ${folders.length} folders to SharedPreferences');
  }

  // Load video folders from persistent cache - OPTIMIZED for speed
  static Future<List<VideoFolder>?> loadFromCache() async {
    final stopwatch = Stopwatch()..start();
    
    // Try database first - using FAST query
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        final folders = await db.getAllFoldersFast();

        if (folders.isNotEmpty) {
          AppLogger.i('⚡ Loaded ${folders.length} folders from database in ${stopwatch.elapsedMilliseconds}ms');
          return folders;
        }
      } catch (e) {
        AppLogger.e('Failed to load from database: $e');
        await _disableDatabase();
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    final result = await _loadFromSharedPreferences();
    if (result != null) {
      AppLogger.i('⚡ Loaded ${result.length} folders from SharedPreferences in ${stopwatch.elapsedMilliseconds}ms');
    }
    return result;
  }

  /// Load from SharedPreferences (fallback)
  static Future<List<VideoFolder>?> _loadFromSharedPreferences() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_cacheKey);

    if (jsonString == null) {
      return null;
    }

    try {
      final jsonList = jsonDecode(jsonString) as List;
      final folders = jsonList
          .map((json) => VideoFolder.fromJson(json as Map<String, dynamic>))
          .toList();
      AppLogger.i('Loaded ${folders.length} folders from SharedPreferences');
      return folders;
    } catch (e) {
      AppLogger.e('Failed to parse cache: $e');
      return null;
    }
  }

  // Get the timestamp of the last cache update
  static Future<DateTime?> getLastCacheTimestamp() async {
    // Check database first
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        final videos = await db.getAllVideos();
        if (videos.isNotEmpty) {
          // Get the most recent update time
          final mostRecent = videos
              .map((v) => v.dateAdded)
              .reduce((a, b) => a.isAfter(b) ? a : b);
          return mostRecent;
        }
      } catch (e) {
        // Fall through to SharedPreferences
      }
    }

    final prefs = await SharedPreferences.getInstance();
    final timestamp = prefs.getInt(_timestampKey);

    if (timestamp == null) {
      return null;
    }

    return DateTime.fromMillisecondsSinceEpoch(timestamp);
  }

  // Clear the persistent cache
  static Future<void> clearCache() async {
    // Clear database
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        await db.deleteAllData();
        AppLogger.i('Database cache cleared');
      } catch (e) {
        AppLogger.e('Failed to clear database: $e');
      }
    }

    // Clear SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_cacheKey);
    await prefs.remove(_timestampKey);
    await prefs.remove(_fileMetadataKey);
    AppLogger.i('SharedPreferences cache cleared');
  }

  // Check if cache is expired (older than specified duration)
  static Future<bool> isCacheExpired(Duration maxAge) async {
    final timestamp = await getLastCacheTimestamp();

    if (timestamp == null) {
      return true; // No cache exists
    }

    final now = DateTime.now();
    return now.difference(timestamp) > maxAge;
  }

  // Save file metadata for incremental scanning (int milliseconds format)
  static Future<void> saveFileMetadataInt(Map<String, int> fileModifiedTimes) async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJson = <String, int>{};

    fileModifiedTimes.forEach((path, modifiedTimeMs) {
      metadataJson[path] = modifiedTimeMs;
    });

    await prefs.setString(_fileMetadataKey, jsonEncode(metadataJson));
  }

  // Save file metadata for incremental scanning
  static Future<void> saveFileMetadata(Map<String, DateTime> fileModifiedTimes) async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJson = <String, int>{};

    fileModifiedTimes.forEach((path, modifiedTime) {
      metadataJson[path] = modifiedTime.millisecondsSinceEpoch;
    });

    await prefs.setString(_fileMetadataKey, jsonEncode(metadataJson));
  }

  // Load file metadata for incremental scanning (returns int milliseconds)
  static Future<Map<String, int>?> loadFileMetadataInt() async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJsonString = prefs.getString(_fileMetadataKey);

    if (metadataJsonString == null) {
      return null;
    }

    try {
      final metadataJson =
          jsonDecode(metadataJsonString) as Map<String, dynamic>;
      final metadata = <String, int>{};

      metadataJson.forEach((path, modifiedTimeMs) {
        metadata[path] = modifiedTimeMs as int;
      });

      return metadata;
    } catch (e) {
      return null;
    }
  }

  // Load file metadata for incremental scanning
  static Future<Map<String, DateTime>?> loadFileMetadata() async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJsonString = prefs.getString(_fileMetadataKey);

    if (metadataJsonString == null) {
      return null;
    }

    try {
      final metadataJson =
          jsonDecode(metadataJsonString) as Map<String, dynamic>;
      final metadata = <String, DateTime>{};

      metadataJson.forEach((path, modifiedTimeMs) {
        metadata[path] =
            DateTime.fromMillisecondsSinceEpoch(modifiedTimeMs as int);
      });

      return metadata;
    } catch (e) {
      return null;
    }
  }

  /// Re-enable database (for testing or recovery)
  static Future<void> enableDatabase() async {
    _databaseAvailable = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_useDatabaseKey, true);
    AppLogger.i('Database re-enabled');
  }
}
