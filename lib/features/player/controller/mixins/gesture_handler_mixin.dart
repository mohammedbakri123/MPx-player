import 'package:flutter/material.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for handling player gestures (horizontal/vertical drag, long press).
///
/// This mixin expects `adjustVolumeByDrag` to be provided by VolumeManagerMixin
/// and `adjustBrightnessByDrag` to be provided by BrightnessManagerMixin
/// when both are used together in PlayerController.
mixin GestureHandlerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  /// Adjusts volume based on vertical drag gesture (provided by VolumeManagerMixin).
  void adjustVolumeByDrag(double delta);

  /// Adjusts brightness based on vertical drag gesture (provided by BrightnessManagerMixin).
  void adjustBrightnessByDrag(double delta);

  void onHorizontalDragStart(double startX) {
    state.dragStartX = startX;
    state.seekStartPosition = state.position;
    state.isDraggingX = true;
    state.showSeekIndicator = true;
    notifyListeners();
  }

  void onHorizontalDragUpdate(double currentX, double screenWidth) {
    final deltaX = currentX - state.dragStartX;
    final seekPercent = deltaX / screenWidth;
    final seekMs = (seekPercent * state.duration.inMilliseconds).toInt();
    final newPosition =
        state.seekStartPosition + Duration(milliseconds: seekMs);

    state.position = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, state.duration.inMilliseconds),
    );
    state.seekDirection = deltaX > 0 ? 'forward' : 'back';
    notifyListeners();
  }

  void onHorizontalDragEnd() {
    repository.seek(state.position);
    state.isDraggingX = false;
    state.showSeekIndicator = false;
    notifyListeners();
    startHideTimer();
  }

  void onVerticalDragStart(String side) {
    state.isDraggingY = true;
    state.verticalDragSide = side;
    if (side == 'left') {
      state.showBrightnessIndicator = true;
    } else {
      state.showVolumeIndicator = true;
    }
    notifyListeners();
  }

  void onVerticalDragUpdate(double delta) {
    if (state.verticalDragSide == 'left') {
      // Delegate to BrightnessManagerMixin
      adjustBrightnessByDrag(delta);
    } else {
      // Delegate to VolumeManagerMixin
      adjustVolumeByDrag(delta);
    }
    notifyListeners();
  }

  void onVerticalDragEnd() {
    state.isDraggingY = false;
    state.showBrightnessIndicator = false;
    state.showVolumeIndicator = false;
    notifyListeners();
    startHideTimer();
  }

  void onLongPressStart() {
    state.isLongPressing = true;
    state.playbackSpeed = 2.0;
    repository.setSpeed(2.0);
    notifyListeners();
  }

  void onLongPressEnd() {
    state.isLongPressing = false;
    state.playbackSpeed = 1.0;
    repository.setSpeed(1.0);
    notifyListeners();
  }

  void startHideTimer();
}
