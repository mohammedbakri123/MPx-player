import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../features/library/domain/entities/video_file.dart';

/// Service to manage the last played video using SharedPreferences.
class LastPlayedService {
  static const String _lastPlayedVideoKey = 'last_played_video';
  static const String _lastPositionKey = 'last_position';

  static SharedPreferences? _prefs;

  /// Initialize the service. Must be called before using other methods.
  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  /// Save the last played video with its playback position.
  static Future<bool> saveLastPlayedVideo(VideoFile video,
      {Duration? position}) async {
    if (_prefs == null) await init();

    final videoJson = jsonEncode(video.toJson());
    final success = await _prefs!.setString(_lastPlayedVideoKey, videoJson);

    if (position != null) {
      await _prefs!.setInt(_lastPositionKey, position.inMilliseconds);
    }

    return success;
  }

  /// Get the last played video.
  static VideoFile? getLastPlayedVideo() {
    if (_prefs == null) return null;

    final jsonString = _prefs!.getString(_lastPlayedVideoKey);
    if (jsonString != null) {
      try {
        final json = jsonDecode(jsonString) as Map<String, dynamic>;
        return VideoFile.fromJson(json);
      } catch (e) {
        return null;
      }
    }
    return null;
  }

  /// Get the last playback position.
  static Duration? getLastPosition() {
    if (_prefs == null) return null;

    final positionMs = _prefs!.getInt(_lastPositionKey);
    if (positionMs != null) {
      return Duration(milliseconds: positionMs);
    }
    return null;
  }

  /// Clear the last played video and position.
  static Future<bool> clearLastPlayedVideo() async {
    if (_prefs == null) await init();

    await _prefs!.remove(_lastPositionKey);
    return await _prefs!.remove(_lastPlayedVideoKey);
  }

  /// Check if there's a last played video.
  static bool hasLastPlayedVideo() {
    if (_prefs == null) return false;
    return _prefs!.getString(_lastPlayedVideoKey) != null;
  }
}
