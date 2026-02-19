import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../features/library/domain/entities/video_file.dart';
import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';

/// Favorites service with SQLite database support
/// Falls back to SharedPreferences if database fails
class FavoritesService {
  static const String _favoritesKey = 'favorite_videos';
  static const String _useDatabaseKey = 'favorites_use_database';
  static SharedPreferences? _prefs;
  static bool _databaseAvailable = true;

  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  /// Check if database is available
  static Future<bool> _isDatabaseAvailable() async {
    if (!_databaseAvailable) return false;
    if (_prefs == null) await init();

    try {
      return _prefs!.getBool(_useDatabaseKey) ?? true;
    } catch (e) {
      return false;
    }
  }

  /// Disable database and fall back to SharedPreferences
  static Future<void> _disableDatabase() async {
    _databaseAvailable = false;
    if (_prefs == null) await init();
    await _prefs!.setBool(_useDatabaseKey, false);
    AppLogger.w(
        'Favorites database disabled, falling back to SharedPreferences');
  }

  static Future<bool> addFavorite(VideoFile video) async {
    if (_prefs == null) await init();

    // Try database first
    if (await _isDatabaseAvailable()) {
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
      } catch (e) {
        AppLogger.e('Failed to add favorite to database: $e');
        await _disableDatabase();
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    return await _addFavoriteSharedPreferences(video);
  }

  /// Add favorite using SharedPreferences (fallback)
  static Future<bool> _addFavoriteSharedPreferences(VideoFile video) async {
    final favorites = await _getFavoritesSharedPreferences();
    if (favorites.any((v) => v.id == video.id)) return true;
    favorites.add(video);
    return _saveFavoritesSharedPreferences(favorites);
  }

  static Future<bool> removeFavorite(String videoId) async {
    if (_prefs == null) await init();

    // Try database first
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        final result = await db.removeFavorite(videoId);
        if (result) {
          AppLogger.i('Removed favorite: $videoId');
          return true;
        }
      } catch (e) {
        AppLogger.e('Failed to remove favorite from database: $e');
        await _disableDatabase();
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    return await _removeFavoriteSharedPreferences(videoId);
  }

  /// Remove favorite using SharedPreferences (fallback)
  static Future<bool> _removeFavoriteSharedPreferences(String videoId) async {
    final favorites = await _getFavoritesSharedPreferences();
    favorites.removeWhere((v) => v.id == videoId);
    return _saveFavoritesSharedPreferences(favorites);
  }

  static Future<List<VideoFile>> getFavorites() async {
    if (_prefs == null) await init();

    // Try database first
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        final favorites = await db.getFavorites();
        AppLogger.i('Loaded ${favorites.length} favorites from database');
        return favorites;
      } catch (e) {
        AppLogger.e('Failed to load favorites from database: $e');
        await _disableDatabase();
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    return await _getFavoritesSharedPreferences();
  }

  /// Get favorites using SharedPreferences (fallback)
  static Future<List<VideoFile>> _getFavoritesSharedPreferences() async {
    final jsonString = _prefs!.getString(_favoritesKey);
    if (jsonString == null) return [];
    try {
      final jsonList = jsonDecode(jsonString) as List;
      final favorites =
          jsonList.map((json) => VideoFile.fromJson(json)).toList();
      AppLogger.i(
          'Loaded ${favorites.length} favorites from SharedPreferences');
      return favorites;
    } catch (e) {
      AppLogger.e('Failed to parse favorites: $e');
      return [];
    }
  }

  static Future<bool> isFavorite(String videoId) async {
    if (_prefs == null) await init();

    // Try database first
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        return await db.isFavorite(videoId);
      } catch (e) {
        // Fall through to SharedPreferences
      }
    }

    // Fallback to SharedPreferences
    final favorites = await _getFavoritesSharedPreferences();
    return favorites.any((v) => v.id == videoId);
  }

  static Future<bool> toggleFavorite(VideoFile video) async {
    final isFav = await isFavorite(video.id);
    if (isFav) {
      return removeFavorite(video.id);
    } else {
      return addFavorite(video);
    }
  }

  static Future<bool> _saveFavoritesSharedPreferences(
      List<VideoFile> favorites) async {
    final jsonList = favorites.map((v) => v.toJson()).toList();
    return _prefs!.setString(_favoritesKey, jsonEncode(jsonList));
  }

  static Future<bool> clearFavorites() async {
    if (_prefs == null) await init();

    // Clear database
    if (await _isDatabaseAvailable()) {
      try {
        final db = AppDatabase();
        await db.clearFavorites();
        AppLogger.i('Cleared favorites from database');
      } catch (e) {
        AppLogger.e('Failed to clear favorites from database: $e');
      }
    }

    // Clear SharedPreferences
    return _prefs!.remove(_favoritesKey);
  }

  /// Re-enable database (for testing or recovery)
  static Future<void> enableDatabase() async {
    _databaseAvailable = true;
    if (_prefs == null) await init();
    await _prefs!.setBool(_useDatabaseKey, true);
    AppLogger.i('Favorites database re-enabled');
  }
}
