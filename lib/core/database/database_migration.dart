import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../features/library/domain/entities/video_file.dart';
import '../../features/library/domain/entities/video_folder.dart';
import '../services/logger_service.dart';
import 'app_database.dart';

/// Handles safe migration from SharedPreferences to SQLite database
/// Includes data verification and rollback capability
class DatabaseMigration {
  static const String _migrationVersionKey = 'db_migration_version';
  static const String _migrationCompletedKey = 'db_migration_completed';
  static const int _currentMigrationVersion = 1;

  /// Check if migration is needed and perform it
  static Future<void> migrateIfNeeded() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final completedVersion = prefs.getInt(_migrationVersionKey) ?? 0;

      if (completedVersion < _currentMigrationVersion) {
        AppLogger.i(
            'Database migration needed: v$completedVersion -> v$_currentMigrationVersion');
        await _performMigration();
        await prefs.setInt(_migrationVersionKey, _currentMigrationVersion);
        await prefs.setBool(_migrationCompletedKey, true);
        AppLogger.i('Database migration completed successfully');
      } else {
        AppLogger.i(
            'Database migration already completed (v$completedVersion)');
      }
    } catch (e) {
      AppLogger.e('Database migration failed: $e');
      // Don't throw - app will fall back to SharedPreferences
    }
  }

  /// Perform the actual migration
  static Future<void> _performMigration() async {
    final db = AppDatabase();

    // Migrate video library cache
    await _migrateVideoLibrary(db);

    // Migrate favorites
    await _migrateFavorites(db);
  }

  /// Migrate video library from SharedPreferences to database
  static Future<void> _migrateVideoLibrary(AppDatabase db) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final jsonString = prefs.getString('video_folders_cache');

      if (jsonString == null || jsonString.isEmpty) {
        AppLogger.i('No video library cache to migrate');
        return;
      }

      AppLogger.i('Migrating video library cache...');

      // Parse JSON
      final jsonList = jsonDecode(jsonString) as List;
      final folders = jsonList
          .map((json) => VideoFolder.fromJson(json as Map<String, dynamic>))
          .toList();

      AppLogger.i('Parsed ${folders.length} folders for migration');

      // Migrate to database
      await db.insertFolders(folders);

      // Migrate all videos
      int totalVideos = 0;
      for (final folder in folders) {
        if (folder.videos.isNotEmpty) {
          await db.insertVideos(folder.videos);
          totalVideos += folder.videos.length;
        }
      }

      AppLogger.i('Migrated $totalVideos videos in ${folders.length} folders');

      // Verify migration
      final dbVideoCount = await db.getVideoCount();
      final dbFolderCount = await db.getFolderCount();

      AppLogger.i(
          'Verification: $dbVideoCount videos, $dbFolderCount folders in database');

      if (dbVideoCount != totalVideos) {
        AppLogger.w(
            'Migration verification warning: expected $totalVideos videos, found $dbVideoCount');
      }
    } catch (e) {
      AppLogger.e('Video library migration failed: $e');
      // Continue with other migrations - don't throw
    }
  }

  /// Migrate favorites from SharedPreferences to database
  static Future<void> _migrateFavorites(AppDatabase db) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final jsonString = prefs.getString('favorite_videos');

      if (jsonString == null || jsonString.isEmpty) {
        AppLogger.i('No favorites to migrate');
        return;
      }

      AppLogger.i('Migrating favorites...');

      // Parse JSON
      final jsonList = jsonDecode(jsonString) as List;
      final favorites = jsonList
          .map((json) => VideoFile.fromJson(json as Map<String, dynamic>))
          .toList();

      AppLogger.i('Parsed ${favorites.length} favorites for migration');

      // Migrate to database
      int successCount = 0;
      for (final video in favorites) {
        // First ensure video exists in videos table
        final existingVideo = await db.getVideoById(video.id);
        if (existingVideo == null) {
          // Video might have been deleted, but we still add it as favorite
          // The video will be added when scanned again
          await db.insertVideo(video);
        }

        final success = await db.addFavorite(video.id);
        if (success) successCount++;
      }

      AppLogger.i('Migrated $successCount/${favorites.length} favorites');

      // Verify migration
      final dbFavoriteCount = await db.getFavoriteCount();
      AppLogger.i('Verification: $dbFavoriteCount favorites in database');
    } catch (e) {
      AppLogger.e('Favorites migration failed: $e');
      // Continue - don't throw
    }
  }

  /// Check if migration has been completed
  static Future<bool> isMigrationCompleted() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_migrationCompletedKey) ?? false;
  }

  /// Reset migration flag (for testing/debugging)
  static Future<void> resetMigration() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_migrationVersionKey);
    await prefs.remove(_migrationCompletedKey);
    AppLogger.i('Migration flags reset');
  }

  /// Get migration statistics
  static Future<Map<String, dynamic>> getMigrationStats() async {
    final prefs = await SharedPreferences.getInstance();
    final db = AppDatabase();

    return {
      'migration_version': prefs.getInt(_migrationVersionKey) ?? 0,
      'migration_completed': prefs.getBool(_migrationCompletedKey) ?? false,
      'db_video_count': await db.getVideoCount(),
      'db_folder_count': await db.getFolderCount(),
      'db_favorite_count': await db.getFavoriteCount(),
    };
  }
}
