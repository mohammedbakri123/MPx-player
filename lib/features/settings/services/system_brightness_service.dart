import 'package:screen_brightness/screen_brightness.dart';
import '../../../core/services/logger_service.dart';

/// Service for managing system screen brightness.
///
/// This service provides a centralized way to control the device's
/// screen brightness across all platforms.
///
/// Brightness is represented as a double from 0.0 to 1.0:
/// - 0.0 = Minimum brightness (darkest)
/// - 1.0 = Maximum brightness (brightest)
class SystemBrightnessService {
  static double _currentBrightness = 1.0;
  static bool _initialized = false;
  static final _screenBrightness = ScreenBrightness();

  /// Initialize the service and get current screen brightness.
  static Future<void> init() async {
    if (_initialized) return;

    try {
      // Get initial brightness
      final brightness = await _screenBrightness.current;
      if (brightness != null) {
        _currentBrightness = brightness;
      }

      _initialized = true;
      AppLogger.i(
          'System brightness service initialized: ${(_currentBrightness * 100).toStringAsFixed(0)}%');
    } catch (e) {
      AppLogger.e('Failed to initialize system brightness service: $e');
      _currentBrightness = 1.0;
      _initialized = true;
    }
  }

  /// Get current screen brightness (0.0 to 1.0).
  static double get brightness => _currentBrightness;

  /// Get current brightness as percentage (0-100).
  static int get brightnessPercent => (_currentBrightness * 100).round();

  /// Set screen brightness.
  ///
  /// [value] - Brightness level from 0.0 to 1.0 (auto-clamped).
  static Future<void> setBrightness(double value) async {
    final clampedValue = value.clamp(0.0, 1.0);
    try {
      await _screenBrightness.setScreenBrightness(clampedValue);
      _currentBrightness = clampedValue;
      AppLogger.d(
          'System brightness set to: ${(clampedValue * 100).toStringAsFixed(0)}%');
    } catch (e) {
      AppLogger.e('Failed to set system brightness: $e');
    }
  }

  /// Set brightness from percentage (0-100).
  static Future<void> setBrightnessFromPercent(double percent) async {
    await setBrightness(percent / 100.0);
  }

  /// Increase brightness by step amount.
  static Future<void> brightnessUp([double step = 0.1]) async {
    await setBrightness(_currentBrightness + step);
  }

  /// Decrease brightness by step amount.
  static Future<void> brightnessDown([double step = 0.1]) async {
    await setBrightness(_currentBrightness - step);
  }

  /// Reset brightness to maximum.
  static Future<void> resetToMax() async {
    await setBrightness(1.0);
  }

  /// Reset brightness to system default.
  static Future<void> resetToSystemDefault() async {
    await _screenBrightness.resetScreenBrightness();
    final brightness = await _screenBrightness.current;
    if (brightness != null) {
      _currentBrightness = brightness;
    }
    AppLogger.d(
        'System brightness reset to default: ${(_currentBrightness * 100).toStringAsFixed(0)}%');
  }
}
