import 'package:flutter/material.dart';
import '../../../settings/services/app_settings_service.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for handling player gestures (horizontal/vertical drag, long press).
///
/// This mixin expects:
/// - `adjustVolumeByDrag` to be provided by VolumeManagerMixin
/// - `adjustBrightnessByDrag` to be provided by BrightnessManagerMixin
///
/// For gesture coordination (priority/locking), apply GestureCoordinatorMixin
/// BEFORE this mixin in the class declaration.
mixin GestureHandlerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  /// Adjusts volume based on vertical drag gesture (provided by VolumeManagerMixin).
  void adjustVolumeByDrag(double delta);

  /// Adjusts brightness based on vertical drag gesture (provided by BrightnessManagerMixin).
  void adjustBrightnessByDrag(double delta);

  // Gesture coordination methods - overridden by calling setupCoordinationMethods
  void Function() _coordOnSeekStart = () {};
  void Function() _coordOnSeekEnd = () {};
  void Function() _coordOnVolumeAdjustStart = () {};
  void Function() _coordOnVolumeAdjustEnd = () {};
  void Function() _coordOnBrightnessAdjustStart = () {};
  void Function() _coordOnBrightnessAdjustEnd = () {};
  bool Function() _coordShouldProcessVerticalDrag = () => true;
  bool Function() _coordShouldProcessLongPress = () => true;

  /// Setup coordination methods - call this when using with GestureCoordinatorMixin
  void setupGestureCoordination({
    required void Function() onSeekStart,
    required void Function() onSeekEnd,
    required void Function() onVolumeAdjustStart,
    required void Function() onVolumeAdjustEnd,
    required void Function() onBrightnessAdjustStart,
    required void Function() onBrightnessAdjustEnd,
    required bool Function() shouldProcessVerticalDrag,
    required bool Function() shouldProcessLongPress,
  }) {
    _coordOnSeekStart = onSeekStart;
    _coordOnSeekEnd = onSeekEnd;
    _coordOnVolumeAdjustStart = onVolumeAdjustStart;
    _coordOnVolumeAdjustEnd = onVolumeAdjustEnd;
    _coordOnBrightnessAdjustStart = onBrightnessAdjustStart;
    _coordOnBrightnessAdjustEnd = onBrightnessAdjustEnd;
    _coordShouldProcessVerticalDrag = shouldProcessVerticalDrag;
    _coordShouldProcessLongPress = shouldProcessLongPress;
  }

  void onHorizontalDragStart(double startX) {
    state.dragStartX = startX;
    state.seekStartPosition = state.position;
    state.isDraggingX = true;
    state.showSeekIndicator = true;
    _coordOnSeekStart();
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
    _coordOnSeekEnd();
    notifyListeners();
    startHideTimer();
  }

  void onVerticalDragStart(String side) {
    if (!AppSettingsService.swipeGesturesEnabled) return;

    // Block vertical drag during horizontal seek to prevent conflicts
    if (!_coordShouldProcessVerticalDrag() || state.isDraggingX) return;

    if (side == 'left') {
      _coordOnBrightnessAdjustStart();
    } else {
      _coordOnVolumeAdjustStart();
    }

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
    _coordOnBrightnessAdjustEnd();
    _coordOnVolumeAdjustEnd();
    notifyListeners();
    startHideTimer();
  }

  void onLongPressStart() {
    if (!AppSettingsService.holdToBoostEnabled) return;

    // Block long-press speed toggle during seek to prevent conflicts
    if (!_coordShouldProcessLongPress()) return;
    if (state.isLongPressing) return;

    state.isLongPressing = true;
    state.speedBeforeLongPress = state.playbackSpeed;
    state.playbackSpeed = 2.0;
    repository.setSpeed(2.0);
    notifyListeners();
  }

  void onLongPressEnd() {
    if (!state.isLongPressing) return;

    state.isLongPressing = false;
    state.playbackSpeed = state.speedBeforeLongPress;
    repository.setSpeed(state.speedBeforeLongPress);
    notifyListeners();
  }

  void startHideTimer();
}
