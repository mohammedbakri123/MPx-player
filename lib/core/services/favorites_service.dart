import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../features/library/domain/entities/video_file.dart';

class FavoritesService {
  static const String _favoritesKey = 'favorite_videos';
  static SharedPreferences? _prefs;

  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  static Future<bool> addFavorite(VideoFile video) async {
    if (_prefs == null) await init();
    final favorites = await getFavorites();
    if (favorites.any((v) => v.id == video.id)) return true;
    favorites.add(video);
    return _saveFavorites(favorites);
  }

  static Future<bool> removeFavorite(String videoId) async {
    if (_prefs == null) await init();
    final favorites = await getFavorites();
    favorites.removeWhere((v) => v.id == videoId);
    return _saveFavorites(favorites);
  }

  static Future<List<VideoFile>> getFavorites() async {
    if (_prefs == null) await init();
    final jsonString = _prefs!.getString(_favoritesKey);
    if (jsonString == null) return [];
    try {
      final jsonList = jsonDecode(jsonString) as List;
      return jsonList.map((json) => VideoFile.fromJson(json)).toList();
    } catch (e) {
      return [];
    }
  }

  static Future<bool> isFavorite(String videoId) async {
    final favorites = await getFavorites();
    return favorites.any((v) => v.id == videoId);
  }

  static Future<bool> toggleFavorite(VideoFile video) async {
    if (await isFavorite(video.id)) {
      return removeFavorite(video.id);
    } else {
      return addFavorite(video);
    }
  }

  static Future<bool> _saveFavorites(List<VideoFile> favorites) async {
    final jsonList = favorites.map((v) => v.toJson()).toList();
    return _prefs!.setString(_favoritesKey, jsonEncode(jsonList));
  }

  static Future<bool> clearFavorites() async {
    if (_prefs == null) await init();
    return _prefs!.remove(_favoritesKey);
  }
}
