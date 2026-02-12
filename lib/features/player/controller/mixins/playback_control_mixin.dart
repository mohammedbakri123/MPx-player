import 'package:flutter/material.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for basic playback control operations.
mixin PlaybackControlMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  Future<void> loadVideo(String path) async {
    await repository.load(path);
    await repository.setVolume(state.volume);
  }

  void togglePlayPause() {
    if (state.isPlaying) {
      repository.pause();
    } else {
      repository.play();
    }
  }

  void seek(Duration position) {
    repository.seek(position);
  }

  void seekBack() {
    final newPosition = state.position - const Duration(seconds: 10);
    repository.seek(newPosition);
    showSeekFeedback('back');
  }

  void seekForward() {
    final newPosition = state.position + const Duration(seconds: 10);
    repository.seek(newPosition);
    showSeekFeedback('forward');
  }

  void changeSpeed() {
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    final idx = speeds.indexOf(state.playbackSpeed);
    final nextSpeed = speeds[(idx + 1) % speeds.length];
    state.playbackSpeed = nextSpeed;
    repository.setSpeed(nextSpeed);
    notifyListeners();
  }

  void setVolume(double value) {
    state.volume = value;
    repository.setVolume(value);
    notifyListeners();
  }

  void setBrightness(double value) {
    state.brightnessValue = value.clamp(0.0, 1.0);
    notifyListeners();
  }

  void toggleFullscreen() {
    state.isFullscreen = !state.isFullscreen;
    notifyListeners();
  }

  void showControlsNow() {
    state.showControls = true;
    notifyListeners();
    startHideTimer();
  }

  void startHideTimer() {
    Future.delayed(const Duration(seconds: 4), () {
      if (state.showControls &&
          !state.isLongPressing &&
          !state.isDraggingX &&
          !state.isDraggingY) {
        state.showControls = false;
        notifyListeners();
      }
    });
  }

  void showSeekFeedback(String direction) {
    state.showSeekIndicator = true;
    state.seekDirection = direction;
    notifyListeners();
    Future.delayed(const Duration(milliseconds: 500), () {
      state.showSeekIndicator = false;
      notifyListeners();
    });
  }
}
