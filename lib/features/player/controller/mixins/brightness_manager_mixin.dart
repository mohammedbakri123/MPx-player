import 'package:flutter/material.dart';
import '../../../settings/services/system_brightness_service.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for managing screen brightness control.
///
/// Provides methods for adjusting brightness and tracking brightness changes.
/// Brightness is synced with the system brightness.
///
/// This mixin provides the `adjustBrightnessByDrag` method required by GestureHandlerMixin.
mixin BrightnessManagerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  /// Adjusts brightness based on vertical drag gesture.
  ///
  /// [delta] - The drag delta value (negative for up, positive for down).
  /// Drag UP increases brightness, drag DOWN decreases brightness.
  int _lastBrightnessDragNotifyMs = 0;

  void adjustBrightnessByDrag(double delta) {
    // Negate delta so dragging UP increases brightness (natural gesture)
    final brightnessUpdate = -delta / 200;
    final newBrightness =
        (state.brightnessValue + brightnessUpdate * 100).clamp(0.0, 100.0);
    state.brightnessValue = newBrightness;
    SystemBrightnessService.setBrightnessFromPercent(newBrightness);
    state.showBrightnessIndicator = true;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastBrightnessDragNotifyMs >= 100) {
      _lastBrightnessDragNotifyMs = now;
      notifyListeners();
    }
  }

  /// Current brightness level (0.0 to 100.0).
  double get brightness => state.brightnessValue;

  /// Initialize brightness from system brightness on startup.
  ///
  /// Loads the current system brightness and applies it to the player.
  Future<void> initializeBrightness() async {
    await SystemBrightnessService.init();
    final systemBrightness = SystemBrightnessService.brightness * 100;
    state.brightnessValue = systemBrightness;
    try {
      notifyListeners();
    } catch (_) {}
  }

  /// Sets the brightness level and updates system brightness.
  ///
  /// [value] - Brightness level from 0.0 (darkest) to 100.0 (brightest).
  /// The value is automatically clamped to the valid range.
  void setBrightness(double value) {
    state.brightnessValue = value.clamp(0.0, 100.0);
    SystemBrightnessService.setBrightnessFromPercent(state.brightnessValue);
    notifyListeners();
  }

  /// Increases brightness by the specified amount.
  ///
  /// [step] - The amount to increase brightness by (default: 10.0).
  void brightnessUp([double step = 10.0]) {
    setBrightness(state.brightnessValue + step);
    _showBrightnessFeedback();
  }

  /// Decreases brightness by the specified amount.
  ///
  /// [step] - The amount to decrease brightness by (default: 10.0).
  void brightnessDown([double step = 10.0]) {
    setBrightness(state.brightnessValue - step);
    _showBrightnessFeedback();
  }

  /// Resets brightness to maximum.
  void brightnessResetToMax() {
    setBrightness(100.0);
    _showBrightnessFeedback();
  }

  /// Resets brightness to system default.
  void brightnessResetToSystemDefault() {
    SystemBrightnessService.resetToSystemDefault();
    state.brightnessValue =
        SystemBrightnessService.brightnessPercent.toDouble();
    _showBrightnessFeedback();
  }

  /// Shows brightness indicator and hides it after a short delay.
  void _showBrightnessFeedback() {
    state.showBrightnessIndicator = true;
    notifyListeners();

    Future.delayed(const Duration(milliseconds: 800), () {
      state.showBrightnessIndicator = false;
      try {
        notifyListeners();
      } catch (_) {}
    });
  }

  /// Hides the brightness indicator.
  void hideBrightnessIndicator() {
    if (state.showBrightnessIndicator) {
      state.showBrightnessIndicator = false;
      notifyListeners();
    }
  }
}
