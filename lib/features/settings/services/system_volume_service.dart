import 'package:flutter_volume_controller/flutter_volume_controller.dart';
import '../../../core/services/logger_service.dart';

/// Service for managing system volume.
///
/// This service provides a centralized way to control the device's
/// system volume across all platforms.
///
/// Volume is represented as a double from 0.0 to 1.0:
/// - 0.0 = Muted
/// - 1.0 = Maximum volume
class SystemVolumeService {
  static double _currentVolume = 1.0;
  static bool _initialized = false;

  /// Initialize the service and get current system volume.
  static Future<void> init() async {
    if (_initialized) return;

    try {
      // Hide system volume slider - use player's own UI instead
      await FlutterVolumeController.updateShowSystemUI(false);

      // Get initial volume
      final volume = await FlutterVolumeController.getVolume();
      if (volume != null) {
        _currentVolume = volume;
      }

      _initialized = true;
      AppLogger.i('System volume service initialized: ${(_currentVolume * 100).toStringAsFixed(0)}%');
    } catch (e) {
      AppLogger.e('Failed to initialize system volume service: $e');
      _currentVolume = 1.0;
      _initialized = true;
    }
  }

  /// Get current system volume (0.0 to 1.0).
  static double get volume => _currentVolume;

  /// Get current volume as percentage (0-100).
  static int get volumePercent => (_currentVolume * 100).round();

  /// Get whether the player is muted.
  static bool get isMuted => _currentVolume == 0;

  /// Set system volume.
  ///
  /// [value] - Volume level from 0.0 to 1.0 (auto-clamped).
  static Future<void> setVolume(double value) async {
    final clampedValue = value.clamp(0.0, 1.0);
    try {
      await FlutterVolumeController.setVolume(clampedValue);
      _currentVolume = clampedValue;
      AppLogger.d('System volume set to: ${(clampedValue * 100).toStringAsFixed(0)}%');
    } catch (e) {
      AppLogger.e('Failed to set system volume: $e');
    }
  }

  /// Set volume from percentage (0-100).
  static Future<void> setVolumeFromPercent(double percent) async {
    await setVolume(percent / 100.0);
  }

  /// Increase volume by step amount.
  static Future<void> volumeUp([double step = 0.1]) async {
    await setVolume(_currentVolume + step);
  }

  /// Decrease volume by step amount.
  static Future<void> volumeDown([double step = 0.1]) async {
    await setVolume(_currentVolume - step);
  }

  /// Toggle mute on/off.
  static Future<void> toggleMute() async {
    if (isMuted) {
      await setVolume(1.0);
    } else {
      await setVolume(0.0);
    }
  }

  /// Mute the system.
  static Future<void> mute() async {
    await setVolume(0.0);
  }

  /// Unmute the system (restore to 1.0).
  static Future<void> unmute() async {
    await setVolume(1.0);
  }

  /// Reset volume to maximum.
  static Future<void> resetToMax() async {
    await setVolume(1.0);
  }
}
