import 'dart:async';
import 'package:sqflite/sqflite.dart';
import '../../services/logger_service.dart';
import '../../../features/history/domain/entities/watch_history_entry.dart';
import '../../../features/library/domain/entities/video_file.dart';

mixin WatchHistoryOperations {
  Future<Database> get database;

  Future<void> addToHistory(WatchHistoryEntry entry) async {
    final db = await database;
    await db.insert(
      'watch_history',
      {
        'video_id': entry.videoId,
        'position_ms': entry.positionMs,
        'duration_ms': entry.durationMs,
        'last_played_at': entry.lastPlayedAt.millisecondsSinceEpoch,
        'completion_percent': entry.completionPercent,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> updateHistoryPosition(
    String videoId, {
    required int positionMs,
    required int durationMs,
  }) async {
    final db = await database;
    final completionPercent =
        durationMs > 0 ? ((positionMs / durationMs) * 100).round() : 0;

    await db.update(
      'watch_history',
      {
        'position_ms': positionMs,
        'duration_ms': durationMs,
        'completion_percent': completionPercent,
        'last_played_at': DateTime.now().millisecondsSinceEpoch,
      },
      where: 'video_id = ?',
      whereArgs: [videoId],
    );
  }

  Future<void> upsertHistory(WatchHistoryEntry entry) async {
    final db = await database;
    await db.insert(
      'watch_history',
      {
        'video_id': entry.videoId,
        'position_ms': entry.positionMs,
        'duration_ms': entry.durationMs,
        'last_played_at': entry.lastPlayedAt.millisecondsSinceEpoch,
        'completion_percent': entry.completionPercent,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<WatchHistoryEntry>> getHistory({int? limit}) async {
    final db = await database;
    final maps = await db.query(
      'watch_history',
      orderBy: 'last_played_at DESC',
      limit: limit,
    );
    return maps.map((map) => _entryFromMap(map)).toList();
  }

  Future<WatchHistoryEntry?> getHistoryEntry(String videoId) async {
    final db = await database;
    final maps = await db.query(
      'watch_history',
      where: 'video_id = ?',
      whereArgs: [videoId],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return _entryFromMap(maps.first);
  }

  Future<List<WatchHistoryEntry>> getContinueWatching({int limit = 10}) async {
    final db = await database;
    final maps = await db.query(
      'watch_history',
      where: 'completion_percent > 0 AND completion_percent < 95',
      orderBy: 'last_played_at DESC',
      limit: limit,
    );
    return maps.map((map) => _entryFromMap(map)).toList();
  }

  Future<int?> getLastPosition(String videoId) async {
    final db = await database;
    final maps = await db.query(
      'watch_history',
      columns: ['position_ms'],
      where: 'video_id = ?',
      whereArgs: [videoId],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return maps.first['position_ms'] as int;
  }

  Future<bool> shouldResume(String videoId, Duration totalDuration) async {
    final entry = await getHistoryEntry(videoId);
    if (entry == null) return false;

    final totalSeconds = totalDuration.inSeconds;
    final positionSeconds = (entry.positionMs / 1000).round();

    if (totalSeconds <= 30) return false;
    if (positionSeconds < 5) return false;
    if (totalSeconds - positionSeconds <= 30) return false;

    return true;
  }

  Future<int> getHistoryCount() async {
    final db = await database;
    final result =
        await db.rawQuery('SELECT COUNT(*) as count FROM watch_history');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> removeFromHistory(String videoId) async {
    final db = await database;
    await db.delete(
      'watch_history',
      where: 'video_id = ?',
      whereArgs: [videoId],
    );
    AppLogger.i('Removed from history: $videoId');
  }

  Future<void> clearHistory() async {
    final db = await database;
    await db.delete('watch_history');
    AppLogger.i('Watch history cleared');
  }

  Future<List<WatchHistoryEntry>> getHistoryWithVideos({
    int? limit,
    int offset = 0,
  }) async {
    final db = await database;
    final maps = await db.rawQuery('''
      SELECT 
        h.video_id, h.position_ms, h.duration_ms, h.last_played_at, h.completion_percent,
        v.path, v.title, v.folder_path, v.folder_name, v.size, v.duration, v.date_added,
        v.width, v.height, v.thumbnail_path
      FROM watch_history h
      INNER JOIN videos v ON h.video_id = v.id
      ORDER BY h.last_played_at DESC
      LIMIT ? OFFSET ?
    ''', [limit ?? 100, offset]);

    return maps.map((map) {
      return WatchHistoryEntry(
        videoId: map['video_id'] as String,
        positionMs: map['position_ms'] as int,
        durationMs: map['duration_ms'] as int,
        lastPlayedAt:
            DateTime.fromMillisecondsSinceEpoch(map['last_played_at'] as int),
        completionPercent: map['completion_percent'] as int,
        video: VideoFile(
          id: map['video_id'] as String,
          path: map['path'] as String,
          title: map['title'] as String,
          folderPath: map['folder_path'] as String,
          folderName: map['folder_name'] as String,
          size: map['size'] as int,
          duration: map['duration'] as int,
          dateAdded:
              DateTime.fromMillisecondsSinceEpoch(map['date_added'] as int),
          width: map['width'] as int?,
          height: map['height'] as int?,
          thumbnailPath: map['thumbnail_path'] as String?,
        ),
      );
    }).toList();
  }

  WatchHistoryEntry _entryFromMap(Map<String, dynamic> map) {
    return WatchHistoryEntry(
      videoId: map['video_id'] as String,
      positionMs: map['position_ms'] as int,
      durationMs: map['duration_ms'] as int,
      lastPlayedAt:
          DateTime.fromMillisecondsSinceEpoch(map['last_played_at'] as int),
      completionPercent: map['completion_percent'] as int,
    );
  }
}
