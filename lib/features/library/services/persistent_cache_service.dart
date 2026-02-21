import '../../../features/library/domain/entities/video_folder.dart';
import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';

/// Persistent cache service using SQLite database
class PersistentCacheService {
  /// Save video folders to database
  static Future<void> saveToCache(List<VideoFolder> folders) async {
    try {
      final db = AppDatabase();
      await db.insertFolders(folders);

      for (final folder in folders) {
        if (folder.videos.isNotEmpty) {
          await db.insertVideos(folder.videos);
        }
      }

      AppLogger.i('Saved ${folders.length} folders to database');
    } catch (e) {
      AppLogger.e('Failed to save to database: $e');
    }
  }

  /// Load video folders from database
  static Future<List<VideoFolder>?> loadFromCache() async {
    try {
      final db = AppDatabase();
      final folders = await db.getAllFoldersFast();
      AppLogger.i('Loaded ${folders.length} folders from database');
      return folders.isNotEmpty ? folders : null;
    } catch (e) {
      AppLogger.e('Failed to load from database: $e');
      return null;
    }
  }

  /// Get the timestamp of the last cache update
  static Future<DateTime?> getLastCacheTimestamp() async {
    try {
      final db = AppDatabase();
      final videos = await db.getAllVideos();
      if (videos.isNotEmpty) {
        final mostRecent = videos
            .map((v) => v.dateAdded)
            .reduce((a, b) => a.isAfter(b) ? a : b);
        return mostRecent;
      }
    } catch (e) {
      AppLogger.e('Failed to get timestamp: $e');
    }
    return null;
  }

  /// Clear the cache
  static Future<void> clearCache() async {
    try {
      final db = AppDatabase();
      await db.deleteAllData();
      AppLogger.i('Database cache cleared');
    } catch (e) {
      AppLogger.e('Failed to clear database: $e');
    }
  }

  /// Check if cache is expired
  static Future<bool> isCacheExpired(Duration maxAge) async {
    final timestamp = await getLastCacheTimestamp();
    if (timestamp == null) return true;
    return DateTime.now().difference(timestamp) > maxAge;
  }
}
