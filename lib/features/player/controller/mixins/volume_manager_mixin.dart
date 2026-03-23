import 'package:flutter/material.dart';
import '../../../settings/services/system_volume_service.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for managing volume control and audio output settings.
///
/// Provides methods for adjusting volume, muting, and tracking volume changes.
/// Volume is synced with the system volume.
///
/// This mixin provides the `adjustVolumeByDrag` method required by GestureHandlerMixin.
mixin VolumeManagerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  /// Adjusts volume based on vertical drag gesture.
  ///
  /// [delta] - The drag delta value (negative for up, positive for down).
  void adjustVolumeByDrag(double delta) {
    final volumeUpdate = -delta / 200;
    final newVolume = (state.volume + volumeUpdate * 100).clamp(0.0, 100.0);
    state.volume = newVolume;
    repository.setVolume(newVolume);
    SystemVolumeService.setVolumeFromPercent(newVolume);
    state.showVolumeIndicator = true;
    notifyListeners();
  }

  /// Current volume level (0.0 to 100.0).
  double get volume => state.volume;

  /// Whether the player is muted.
  bool get isMuted => state.volume == 0;

  /// Initialize volume from system volume on startup.
  ///
  /// Loads the current system volume and applies it to the player.
  Future<void> initializeVolume() async {
    await SystemVolumeService.init();
    final systemVolume = SystemVolumeService.volume * 100;
    state.volume = systemVolume;
    repository.setVolume(systemVolume);
    try {
      notifyListeners();
    } catch (_) {}
  }

  /// Sets the volume level and updates system volume.
  ///
  /// [value] - Volume level from 0.0 (mute) to 100.0 (max).
  /// The value is automatically clamped to the valid range.
  void setVolume(double value) {
    state.volume = value.clamp(0.0, 100.0);
    repository.setVolume(state.volume);
    SystemVolumeService.setVolumeFromPercent(state.volume);
    notifyListeners();
  }

  /// Increases volume by the specified amount.
  ///
  /// [step] - The amount to increase volume by (default: 10.0).
  void volumeUp([double step = 10.0]) {
    setVolume(state.volume + step);
    _showVolumeFeedback();
  }

  /// Decreases volume by the specified amount.
  ///
  /// [step] - The amount to decrease volume by (default: 10.0).
  void volumeDown([double step = 10.0]) {
    setVolume(state.volume - step);
    _showVolumeFeedback();
  }

  /// Toggles mute on/off.
  void toggleMute() {
    if (isMuted) {
      state.volume = 100.0;
    } else {
      state.volume = 0.0;
    }
    repository.setVolume(state.volume);
    SystemVolumeService.setVolumeFromPercent(state.volume);
    notifyListeners();
  }

  /// Mutes the player.
  void mute() {
    if (!isMuted) {
      state.volume = 0.0;
      repository.setVolume(state.volume);
      SystemVolumeService.setVolumeFromPercent(state.volume);
      notifyListeners();
    }
  }

  /// Unmutes the player (restores to 100).
  void unmute() {
    if (isMuted) {
      state.volume = 100.0;
      repository.setVolume(state.volume);
      SystemVolumeService.setVolumeFromPercent(state.volume);
      notifyListeners();
    }
  }

  /// Shows volume indicator and hides it after a short delay.
  void _showVolumeFeedback() {
    state.showVolumeIndicator = true;
    notifyListeners();

    Future.delayed(const Duration(milliseconds: 800), () {
      state.showVolumeIndicator = false;
      try {
        notifyListeners();
      } catch (_) {}
    });
  }

  /// Hides the volume indicator.
  void hideVolumeIndicator() {
    if (state.showVolumeIndicator) {
      state.showVolumeIndicator = false;
      notifyListeners();
    }
  }
}
