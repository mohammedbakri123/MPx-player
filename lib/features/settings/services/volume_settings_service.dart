import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';

/// Service for managing volume settings with database persistence.
///
/// This service provides a centralized way to manage volume preferences
/// across the app. Volume is automatically saved to and loaded from
/// the database.
class VolumeSettingsService {
  static double _volume = 100.0;
  static bool _initialized = false;

  /// Initialize the service by loading saved volume from database.
  static Future<void> init() async {
    if (_initialized) return;

    try {
      final db = AppDatabase();
      final savedVolume = await db.getSavedVolume();
      if (savedVolume != null) {
        _volume = savedVolume;
      }
      _initialized = true;
      AppLogger.d('Volume settings initialized: ${_volume.toStringAsFixed(1)}');
    } catch (e) {
      AppLogger.e('Failed to initialize volume settings: $e');
      _volume = 100.0; // Default volume
      _initialized = true;
    }
  }

  /// Get current volume level (0.0 to 100.0).
  static double get volume => _volume;

  /// Get whether the player is muted.
  static bool get isMuted => _volume == 0;

  /// Set volume level and save to database.
  ///
  /// [value] - Volume level from 0.0 to 100.0 (auto-clamped).
  static Future<void> setVolume(double value) async {
    final clampedValue = value.clamp(0.0, 100.0);
    _volume = clampedValue;

    try {
      final db = AppDatabase();
      await db.saveVolume(clampedValue);
    } catch (e) {
      AppLogger.e('Failed to save volume: $e');
    }
  }

  /// Increase volume by step amount.
  static Future<void> volumeUp([double step = 10.0]) async {
    await setVolume(_volume + step);
  }

  /// Decrease volume by step amount.
  static Future<void> volumeDown([double step = 10.0]) async {
    await setVolume(_volume - step);
  }

  /// Toggle mute on/off.
  static Future<void> toggleMute() async {
    if (isMuted) {
      await setVolume(100.0);
    } else {
      await setVolume(0.0);
    }
  }

  /// Mute the player.
  static Future<void> mute() async {
    await setVolume(0.0);
  }

  /// Unmute the player (restore to 100).
  static Future<void> unmute() async {
    await setVolume(100.0);
  }

  /// Reset volume to default (100.0).
  static Future<void> resetToDefaults() async {
    await setVolume(100.0);
  }

  /// Get volume as a percentage (0-100).
  static int get volumePercent => _volume.round();

  /// Get volume as a decimal (0.0-1.0) for sliders.
  static double get volumeDecimal => _volume / 100.0;

  /// Set volume from decimal (0.0-1.0).
  static Future<void> setVolumeFromDecimal(double decimal) async {
    await setVolume((decimal * 100).clamp(0.0, 100.0));
  }
}
