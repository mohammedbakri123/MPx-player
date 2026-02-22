import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';
import '../../library/domain/entities/video_file.dart';
import '../domain/entities/watch_history_entry.dart';

class HistoryService {
  static Future<void> recordPlayback({
    required VideoFile video,
    required Duration position,
    required Duration duration,
  }) async {
    try {
      final positionMs = position.inMilliseconds;
      final durationMs = duration.inMilliseconds;
      final completionPercent =
          durationMs > 0 ? ((positionMs / durationMs) * 100).round() : 0;

      final entry = WatchHistoryEntry(
        videoId: video.id,
        positionMs: positionMs,
        durationMs: durationMs,
        lastPlayedAt: DateTime.now(),
        completionPercent: completionPercent,
      );

      final db = AppDatabase();
      await db.upsertHistory(entry);

      final dbVideo = await db.getVideoById(video.id);
      if (dbVideo == null) {
        await db.insertVideo(video);
      }

      AppLogger.d(
          'Recorded playback: ${video.title} at ${position.inSeconds}s ($completionPercent%)');
    } catch (e) {
      AppLogger.e('Failed to record playback: $e');
    }
  }

  static Future<List<WatchHistoryEntry>> getHistory({int? limit}) async {
    try {
      final db = AppDatabase();
      return await db.getHistoryWithVideos(limit: limit);
    } catch (e) {
      AppLogger.e('Failed to get history: $e');
      return [];
    }
  }

  static Future<List<WatchHistoryEntry>> getContinueWatching(
      {int limit = 10}) async {
    try {
      final db = AppDatabase();
      return await db.getContinueWatching(limit: limit);
    } catch (e) {
      AppLogger.e('Failed to get continue watching: $e');
      return [];
    }
  }

  static Future<WatchHistoryEntry?> getHistoryEntry(String videoId) async {
    try {
      final db = AppDatabase();
      return await db.getHistoryEntry(videoId);
    } catch (e) {
      AppLogger.e('Failed to get history entry: $e');
      return null;
    }
  }

  static Future<Duration?> getLastPosition(String videoId) async {
    try {
      final db = AppDatabase();
      final positionMs = await db.getLastPosition(videoId);
      if (positionMs != null) {
        return Duration(milliseconds: positionMs);
      }
    } catch (e) {
      AppLogger.e('Failed to get last position: $e');
    }
    return null;
  }

  static Future<bool> shouldResume(
      String videoId, Duration totalDuration) async {
    try {
      final db = AppDatabase();
      return await db.shouldResume(videoId, totalDuration);
    } catch (e) {
      AppLogger.e('Failed to check should resume: $e');
      return false;
    }
  }

  static Future<int> getHistoryCount() async {
    try {
      final db = AppDatabase();
      return await db.getHistoryCount();
    } catch (e) {
      AppLogger.e('Failed to get history count: $e');
      return 0;
    }
  }

  static Future<void> removeFromHistory(String videoId) async {
    try {
      final db = AppDatabase();
      await db.removeFromHistory(videoId);
      AppLogger.i('Removed from history: $videoId');
    } catch (e) {
      AppLogger.e('Failed to remove from history: $e');
    }
  }

  static Future<void> clearHistory() async {
    try {
      final db = AppDatabase();
      await db.clearHistory();
      AppLogger.i('History cleared');
    } catch (e) {
      AppLogger.e('Failed to clear history: $e');
    }
  }

  static Future<VideoFile?> getLastPlayedVideo() async {
    try {
      final db = AppDatabase();
      final history = await db.getHistoryWithVideos(limit: 1);
      if (history.isNotEmpty && history.first.video != null) {
        return history.first.video;
      }
    } catch (e) {
      AppLogger.e('Failed to get last played video: $e');
    }
    return null;
  }
}
