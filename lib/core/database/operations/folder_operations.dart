import 'dart:async';
import 'package:sqflite/sqflite.dart';
import '../../services/logger_service.dart';
import '../../../features/library/domain/entities/video_folder.dart';
import '../../../features/library/domain/entities/video_file.dart';
import 'video_operations.dart';

/// Database operations for folders table
mixin FolderDatabaseOperations implements VideoDatabaseOperations {
  @override
  Future<Database> get database;

  /// Insert or update a folder
  Future<void> insertFolder(VideoFolder folder) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.insert(
      'folders',
      {
        'path': folder.path,
        'name': folder.name,
        'video_count': folder.videoCount,
        'thumbnail_path':
            folder.videos.isNotEmpty ? folder.videos.first.thumbnailPath : null,
        'updated_at': now,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Insert multiple folders in a transaction
  Future<void> insertFolders(List<VideoFolder> folders) async {
    if (folders.isEmpty) return;

    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.transaction((txn) async {
      final batch = txn.batch();
      for (final folder in folders) {
        batch.insert(
          'folders',
          {
            'path': folder.path,
            'name': folder.name,
            'video_count': folder.videoCount,
            'thumbnail_path': folder.videos.isNotEmpty
                ? folder.videos.first.thumbnailPath
                : null,
            'updated_at': now,
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await batch.commit(noResult: true);
    });
    AppLogger.i('Inserted ${folders.length} folders into database');
  }

  /// Get all folders - FAST version (single query with JOIN)
  Future<List<VideoFolder>> getAllFoldersFast() async {
    final db = await database;
    final stopwatch = Stopwatch()..start();

    final maps = await db.rawQuery('''
      SELECT 
        f.path, f.name, f.video_count,
        v.id, v.path as video_path, v.title, v.folder_path, 
        v.folder_name, v.size, v.duration, v.date_added,
        v.thumbnail_path, v.width, v.height
      FROM folders f
      LEFT JOIN videos v ON f.path = v.folder_path
      ORDER BY f.path, v.date_added DESC
    ''');

    final folderMap = <String, VideoFolder>{};
    final videoMap = <String, List<VideoFile>>{};
    int videosWithThumbnails = 0;
    int videosWithResolution = 0;

    for (final row in maps) {
      final folderPath = row['path'] as String;
      final folderName = row['name'] as String;

      if (!folderMap.containsKey(folderPath)) {
        folderMap[folderPath] =
            VideoFolder(path: folderPath, name: folderName, videos: []);
        videoMap[folderPath] = [];
      }

      if (row['id'] != null) {
        final video = _videoFromMap(row);
        videoMap[folderPath]!.add(video);
        if (video.thumbnailPath != null) videosWithThumbnails++;
        if (video.width != null && video.height != null) videosWithResolution++;
      }
    }

    final folders = <VideoFolder>[];
    for (final entry in folderMap.entries) {
      folders.add(VideoFolder(
        path: entry.key,
        name: entry.value.name,
        videos: videoMap[entry.key] ?? [],
      ));
    }

    stopwatch.stop();
    AppLogger.i(
        '⚡ Loaded ${folders.length} folders (${folders.fold(0, (sum, f) => sum + f.videos.length)} videos) '
        'in ${stopwatch.elapsedMilliseconds}ms | Thumbnails: $videosWithThumbnails | Resolution: $videosWithResolution');
    return folders;
  }

  /// Get all folders (legacy)
  Future<List<VideoFolder>> getAllFolders() async => getAllFoldersFast();

  /// Delete a folder
  Future<void> deleteFolder(String path) async {
    final db = await database;
    await db.delete('folders', where: 'path = ?', whereArgs: [path]);
  }

  /// Delete all folders
  Future<void> deleteAllFolders() async {
    final db = await database;
    await db.delete('folders');
  }

  /// Get folder count
  Future<int> getFolderCount() async {
    final db = await database;
    final result = await db.rawQuery('SELECT COUNT(*) as count FROM folders');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Get video count for a specific folder
  Future<int> getVideoCountByFolder(String folderPath) async {
    final db = await database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) as count FROM videos WHERE folder_path = ?',
      [folderPath],
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  /// Update folder video count
  Future<void> updateFolderVideoCount(String folderPath, int count) async {
    final db = await database;
    await db.update(
      'folders',
      {
        'video_count': count,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'path = ?',
      whereArgs: [folderPath],
    );
  }

  /// Convert map to VideoFile (needed for folder operations)
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
