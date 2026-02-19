import 'dart:async';
import 'package:sqflite/sqflite.dart';
import '../../services/logger_service.dart';
import '../../../features/library/domain/entities/video_file.dart';

/// Database operations for favorites table
mixin FavoritesDatabaseOperations {
  Future<Database> get database;

  /// Add video to favorites
  Future<bool> addFavorite(String videoId) async {
    try {
      final db = await database;
      await db.insert(
        'favorites',
        {'video_id': videoId, 'added_at': DateTime.now().millisecondsSinceEpoch},
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return true;
    } catch (e) {
      AppLogger.e('Error adding favorite: $e');
      return false;
    }
  }

  /// Remove video from favorites
  Future<bool> removeFavorite(String videoId) async {
    try {
      final db = await database;
      await db.delete('favorites', where: 'video_id = ?', whereArgs: [videoId]);
      return true;
    } catch (e) {
      AppLogger.e('Error removing favorite: $e');
      return false;
    }
  }

  /// Toggle favorite status
  Future<bool> toggleFavorite(String videoId) async {
    final isFav = await isFavorite(videoId);
    return isFav ? await removeFavorite(videoId) : await addFavorite(videoId);
  }

  /// Get all favorite videos
  Future<List<VideoFile>> getFavorites() async {
    final db = await database;
    final maps = await db.rawQuery('''
      SELECT v.* FROM videos v
      INNER JOIN favorites f ON v.id = f.video_id
      ORDER BY f.added_at DESC
    ''');
    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Check if video is favorite
  Future<bool> isFavorite(String videoId) async {
    final db = await database;
    final maps = await db.query('favorites', where: 'video_id = ?', whereArgs: [videoId], limit: 1);
    return maps.isNotEmpty;
  }

  /// Clear all favorites
  Future<void> clearFavorites() async {
    final db = await database;
    await db.delete('favorites');
  }

  /// Get favorite count
  Future<int> getFavoriteCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM favorites');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Convert map to VideoFile
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
