import 'package:flutter/material.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for handling player gestures (horizontal/vertical drag, long press).
mixin GestureHandlerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

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
      final brightnessUpdate = -delta / 200;
      state.brightnessValue =
          (state.brightnessValue + brightnessUpdate).clamp(0.0, 1.0);
    } else {
      final volumeUpdate = -delta / 200;
      state.volume = (state.volume + volumeUpdate * 100).clamp(0, 100);
      repository.setVolume(state.volume);
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
