import 'dart:async';
import 'package:sqflite/sqflite.dart';
import '../../services/logger_service.dart';
import '../../../features/library/domain/entities/video_file.dart';

/// Database operations for videos table
mixin VideoDatabaseOperations {
  Future<Database> get database;

  /// Insert or update a video
  Future<void> insertVideo(VideoFile video) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.insert(
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

  /// Insert multiple videos in a transaction
  Future<void> insertVideos(List<VideoFile> videos) async {
    final db = await database;
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
    AppLogger.i('Inserted ${videos.length} videos into database');
  }

  /// Get all videos
  Future<List<VideoFile>> getAllVideos() async {
    final db = await database;
    final maps = await db.query('videos');
    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Get videos by folder path
  Future<List<VideoFile>> getVideosByFolder(String folderPath) async {
    final db = await database;
    final maps = await db.query(
      'videos',
      where: 'folder_path = ?',
      whereArgs: [folderPath],
      orderBy: 'date_added DESC',
    );
    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Get video by ID
  Future<VideoFile?> getVideoById(String id) async {
    final db = await database;
    final maps = await db.query(
      'videos',
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return _videoFromMap(maps.first);
  }

  /// Search videos by title
  Future<List<VideoFile>> searchVideos(String query) async {
    final db = await database;
    final maps = await db.query(
      'videos',
      where: 'title LIKE ?',
      whereArgs: ['%$query%'],
      orderBy: 'title ASC',
    );
    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Update video thumbnail path
  Future<void> updateVideoThumbnail(String videoId, String thumbnailPath) async {
    final db = await database;
    await db.update(
      'videos',
      {
        'thumbnail_path': thumbnailPath,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [videoId],
    );
  }

  /// Update video metadata (width, height)
  Future<void> updateVideoMetadata(String videoId, int width, int height) async {
    final db = await database;
    await db.update(
      'videos',
      {
        'width': width,
        'height': height,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [videoId],
    );
  }

  /// Batch update multiple videos
  Future<void> updateVideosBatch(List<VideoFile> videos) async {
    if (videos.isEmpty) return;
    
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.transaction((txn) async {
      final batch = txn.batch();
      for (final video in videos) {
        batch.update(
          'videos',
          {
            'thumbnail_path': video.thumbnailPath,
            'width': video.width,
            'height': video.height,
            'updated_at': now,
          },
          where: 'id = ?',
          whereArgs: [video.id],
        );
      }
      await batch.commit(noResult: true);
    });
    AppLogger.d('Updated ${videos.length} videos in database');
  }

  /// Delete a video
  Future<void> deleteVideo(String id) async {
    final db = await database;
    await db.delete('videos', where: 'id = ?', whereArgs: [id]);
  }

  /// Delete all videos
  Future<void> deleteAllVideos() async {
    final db = await database;
    await db.delete('videos');
    AppLogger.i('All videos deleted from database');
  }

  /// Get video count
  Future<int> getVideoCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM videos');
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
