import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../../features/library/domain/entities/video_file.dart';
import 'logger_service.dart';

/// Represents a single entry in the video playback history
class PlayHistoryEntry {
  final String videoId;
  final String videoPath;
  final String title;
  final int lastPositionMs;
  final int totalDurationMs;
  final int lastWatchedMs;
  final double progressPercent;

  PlayHistoryEntry({
    required this.videoId,
    required this.videoPath,
    required this.title,
    required this.lastPositionMs,
    required this.totalDurationMs,
    required this.lastWatchedMs,
    required this.progressPercent,
  });

  /// Get last position as Duration
  Duration get lastPosition => Duration(milliseconds: lastPositionMs);

  /// Get total duration as Duration
  Duration get totalDuration => Duration(milliseconds: totalDurationMs);

  /// Get last watched timestamp as DateTime
  DateTime get lastWatched =>
      DateTime.fromMillisecondsSinceEpoch(lastWatchedMs);

  /// Convert to JSON map
  Map<String, dynamic> toJson() {
    return {
      'videoId': videoId,
      'videoPath': videoPath,
      'title': title,
      'lastPositionMs': lastPositionMs,
      'totalDurationMs': totalDurationMs,
      'lastWatchedMs': lastWatchedMs,
      'progressPercent': progressPercent,
    };
  }

  /// Create from JSON map
  factory PlayHistoryEntry.fromJson(Map<String, dynamic> json) {
    return PlayHistoryEntry(
      videoId: json['videoId'] as String,
      videoPath: json['videoPath'] as String,
      title: json['title'] as String,
      lastPositionMs: json['lastPositionMs'] as int,
      totalDurationMs: json['totalDurationMs'] as int,
      lastWatchedMs: json['lastWatchedMs'] as int,
      progressPercent: (json['progressPercent'] as num).toDouble(),
    );
  }

  /// Create a copy with updated position and timestamp
  PlayHistoryEntry copyWith({
    int? lastPositionMs,
    int? totalDurationMs,
    int? lastWatchedMs,
    double? progressPercent,
  }) {
    return PlayHistoryEntry(
      videoId: videoId,
      videoPath: videoPath,
      title: title,
      lastPositionMs: lastPositionMs ?? this.lastPositionMs,
      totalDurationMs: totalDurationMs ?? this.totalDurationMs,
      lastWatchedMs: lastWatchedMs ?? this.lastWatchedMs,
      progressPercent: progressPercent ?? this.progressPercent,
    );
  }
}

/// Service for managing video playback history and positions
/// Uses singleton pattern with static methods
class PlayHistoryService {
  static const String _playHistoryKey = 'play_history';
  static const int _maxHistoryEntries = 100;
  static const int _resumeThresholdSeconds = 30;

  static SharedPreferences? _prefs;

  /// Initialize the service
  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
    AppLogger.i('PlayHistoryService initialized');
  }

  /// Save or update a video's playback position
  static Future<bool> savePosition(
    VideoFile video,
    Duration position,
    Duration duration,
  ) async {
    try {
      if (_prefs == null) await init();

      final now = DateTime.now();
      final positionMs = position.inMilliseconds;
      final durationMs = duration.inMilliseconds;

      // Calculate progress percentage (0.0 - 1.0)
      double progressPercent = 0.0;
      if (durationMs > 0) {
        progressPercent = (positionMs / durationMs).clamp(0.0, 1.0);
      }

      final entry = PlayHistoryEntry(
        videoId: video.id,
        videoPath: video.path,
        title: video.title,
        lastPositionMs: positionMs,
        totalDurationMs: durationMs,
        lastWatchedMs: now.millisecondsSinceEpoch,
        progressPercent: progressPercent,
      );

      final history = await _getHistory();

      // Remove existing entry for this video if present
      history.removeWhere((e) => e.videoId == video.id);

      // Add new entry at the beginning (most recent first)
      history.insert(0, entry);

      // Apply cleanup to keep only last 100 entries
      final trimmedHistory = _cleanupHistory(history);

      final result = await _saveHistory(trimmedHistory);

      if (result) {
        AppLogger.i(
          'Saved position for video: ${video.title} at ${position.inSeconds}s',
        );
      }

      return result;
    } catch (e) {
      AppLogger.e('Failed to save position for video: ${video.title}', e);
      return false;
    }
  }

  /// Get saved position for a video (returns null if not found)
  static Future<Duration?> getPosition(String videoId) async {
    try {
      if (_prefs == null) await init();

      final history = await _getHistory();
      final entry = history.cast<PlayHistoryEntry?>().firstWhere(
            (e) => e?.videoId == videoId,
            orElse: () => null,
          );

      if (entry == null) {
        return null;
      }

      return Duration(milliseconds: entry.lastPositionMs);
    } catch (e) {
      AppLogger.e('Failed to get position for video: $videoId', e);
      return null;
    }
  }

  /// Alias for getPosition
  static Future<Duration?> getLastPosition(String videoId) {
    return getPosition(videoId);
  }

  /// Check if video should offer resume option
  /// Returns true if position exists and is not within last 30 seconds of video
  static Future<bool> shouldResume(
    String videoId,
    Duration totalDuration,
  ) async {
    try {
      if (_prefs == null) await init();

      final position = await getPosition(videoId);

      if (position == null) {
        return false;
      }

      final totalSeconds = totalDuration.inSeconds;
      final positionSeconds = position.inSeconds;

      // Don't offer resume if video is less than 30 seconds long
      if (totalSeconds <= _resumeThresholdSeconds) {
        return false;
      }

      // Don't offer resume if within last 30 seconds
      final remainingSeconds = totalSeconds - positionSeconds;
      if (remainingSeconds <= _resumeThresholdSeconds) {
        return false;
      }

      // Don't offer resume if at the very beginning (less than 5 seconds in)
      if (positionSeconds < 5) {
        return false;
      }

      return true;
    } catch (e) {
      AppLogger.e('Failed to check shouldResume for video: $videoId', e);
      return false;
    }
  }

  /// Remove all entries from history
  static Future<bool> clearHistory() async {
    try {
      if (_prefs == null) await init();

      final result = await _prefs!.remove(_playHistoryKey);
      AppLogger.i('Play history cleared');
      return result;
    } catch (e) {
      AppLogger.e('Failed to clear play history', e);
      return false;
    }
  }

  /// Remove specific video from history
  static Future<bool> removeEntry(String videoId) async {
    try {
      if (_prefs == null) await init();

      final history = await _getHistory();
      final initialLength = history.length;

      history.removeWhere((e) => e.videoId == videoId);

      if (history.length == initialLength) {
        // Entry not found
        return true;
      }

      final result = await _saveHistory(history);
      AppLogger.i('Removed history entry for video: $videoId');
      return result;
    } catch (e) {
      AppLogger.e('Failed to remove history entry for video: $videoId', e);
      return false;
    }
  }

  /// Get recent entries sorted by lastWatched (most recent first)
  static Future<List<PlayHistoryEntry>> getRecentHistory(
      {int limit = 20}) async {
    try {
      if (_prefs == null) await init();

      final history = await _getHistory();

      // Already sorted by lastWatched (most recent first) due to insert at 0
      // But let's ensure proper sorting
      history.sort((a, b) => b.lastWatchedMs.compareTo(a.lastWatchedMs));

      if (limit > 0 && history.length > limit) {
        return history.sublist(0, limit);
      }

      return history;
    } catch (e) {
      AppLogger.e('Failed to get recent history', e);
      return [];
    }
  }

  /// Get history entry for a specific video
  static Future<PlayHistoryEntry?> getEntry(String videoId) async {
    try {
      if (_prefs == null) await init();

      final history = await _getHistory();
      return history.cast<PlayHistoryEntry?>().firstWhere(
            (e) => e?.videoId == videoId,
            orElse: () => null,
          );
    } catch (e) {
      AppLogger.e('Failed to get entry for video: $videoId', e);
      return null;
    }
  }

  /// Get total number of history entries
  static Future<int> getHistoryCount() async {
    try {
      if (_prefs == null) await init();

      final history = await _getHistory();
      return history.length;
    } catch (e) {
      AppLogger.e('Failed to get history count', e);
      return 0;
    }
  }

  // Private helper methods

  /// Load history from SharedPreferences
  static Future<List<PlayHistoryEntry>> _getHistory() async {
    try {
      final jsonString = _prefs!.getString(_playHistoryKey);

      if (jsonString == null || jsonString.isEmpty) {
        return [];
      }

      final jsonList = jsonDecode(jsonString) as List<dynamic>;
      return jsonList
          .map(
              (json) => PlayHistoryEntry.fromJson(json as Map<String, dynamic>))
          .toList();
    } catch (e) {
      AppLogger.e('Failed to parse play history', e);
      return [];
    }
  }

  /// Save history to SharedPreferences
  static Future<bool> _saveHistory(List<PlayHistoryEntry> history) async {
    try {
      final jsonList = history.map((e) => e.toJson()).toList();
      final jsonString = jsonEncode(jsonList);
      return await _prefs!.setString(_playHistoryKey, jsonString);
    } catch (e) {
      AppLogger.e('Failed to save play history', e);
      return false;
    }
  }

  /// Cleanup history to keep only the most recent entries
  static List<PlayHistoryEntry> _cleanupHistory(
      List<PlayHistoryEntry> history) {
    if (history.length <= _maxHistoryEntries) {
      return history;
    }

    // Keep only the most recent _maxHistoryEntries
    // Since list is sorted by insertion (newest first), take first 100
    AppLogger.i(
        'Cleaning up play history: ${history.length} -> $_maxHistoryEntries');
    return history.sublist(0, _maxHistoryEntries);
  }
}
