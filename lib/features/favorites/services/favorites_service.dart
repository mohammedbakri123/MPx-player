import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';
import '../../library/domain/entities/video_file.dart';

/// Favorites service with SQLite database support
class FavoritesService {
  static Future<void> init() async {
    // SharedPreferences no longer needed for favorites
    AppLogger.i('FavoritesService initialized');
  }

  static Future<bool> addFavorite(VideoFile video) async {
    try {
      final db = AppDatabase();

      // Ensure video exists in database
      final existingVideo = await db.getVideoById(video.id);
      if (existingVideo == null) {
        await db.insertVideo(video);
      }

      final result = await db.addFavorite(video.id);
      if (result) {
        AppLogger.i('Added favorite: ${video.title}');
        return true;
      }
      return false;
    } catch (e) {
      AppLogger.e('Failed to add favorite to database: $e');
      return false;
    }
  }

  static Future<bool> removeFavorite(String videoId) async {
    try {
      final db = AppDatabase();
      final result = await db.removeFavorite(videoId);
      if (result) {
        AppLogger.i('Removed favorite: $videoId');
        return true;
      }
      return false;
    } catch (e) {
      AppLogger.e('Failed to remove favorite from database: $e');
      return false;
    }
  }

  static Future<List<VideoFile>> getFavorites() async {
    try {
      final db = AppDatabase();
      final favorites = await db.getFavorites();
      AppLogger.i('Loaded ${favorites.length} favorites from database');
      return favorites;
    } catch (e) {
      AppLogger.e('Failed to load favorites from database: $e');
      return [];
    }
  }

  static Future<bool> isFavorite(String videoId) async {
    try {
      final db = AppDatabase();
      return await db.isFavorite(videoId);
    } catch (e) {
      AppLogger.e('Failed to check favorite status in database: $e');
      return false;
    }
  }

  static Future<bool> toggleFavorite(VideoFile video) async {
    final isFav = await isFavorite(video.id);
    if (isFav) {
      return removeFavorite(video.id);
    } else {
      return addFavorite(video);
    }
  }

  static Future<bool> clearFavorites() async {
    try {
      final db = AppDatabase();
      await db.clearFavorites();
      AppLogger.i('Cleared favorites from database');
      return true;
    } catch (e) {
      AppLogger.e('Failed to clear favorites from database: $e');
      return false;
    }
  }
}
