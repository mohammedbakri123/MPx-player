import 'package:shared_preferences/shared_preferences.dart';

import '../../features/library/domain/entities/video_file.dart';
import 'logger_service.dart';

/// Simple service for saving and restoring video playback positions
/// Uses singleton pattern with static methods
/// Stores only the last position for each video (no history list)
class PlayHistoryService {
  static const String _positionKeyPrefix = 'video_position_';
  static const int _resumeThresholdSeconds = 30;

  static SharedPreferences? _prefs;

  /// Initialize the service
  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
    AppLogger.i('PlayHistoryService initialized');
  }

  /// Save a video's playback position
  static Future<bool> savePosition(
    VideoFile video,
    Duration position,
    Duration duration,
  ) async {
    try {
      if (_prefs == null) await init();

      final positionMs = position.inMilliseconds;
      final key = _positionKeyPrefix + video.id;

      final result = await _prefs!.setInt(key, positionMs);

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

      final key = _positionKeyPrefix + videoId;
      final positionMs = _prefs!.getInt(key);

      if (positionMs == null) {
        return null;
      }

      return Duration(milliseconds: positionMs);
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

  /// Clear saved position for a specific video
  static Future<bool> clearPosition(String videoId) async {
    try {
      if (_prefs == null) await init();

      final key = _positionKeyPrefix + videoId;
      final result = await _prefs!.remove(key);
      AppLogger.i('Cleared position for video: $videoId');
      return result;
    } catch (e) {
      AppLogger.e('Failed to clear position for video: $videoId', e);
      return false;
    }
  }

  /// Clear all saved positions
  static Future<bool> clearAllPositions() async {
    try {
      if (_prefs == null) await init();

      final keys = _prefs!.getKeys();
      final positionKeys =
          keys.where((key) => key.startsWith(_positionKeyPrefix));

      for (final key in positionKeys) {
        await _prefs!.remove(key);
      }

      AppLogger.i('Cleared all video positions');
      return true;
    } catch (e) {
      AppLogger.e('Failed to clear all positions', e);
      return false;
    }
  }
}
