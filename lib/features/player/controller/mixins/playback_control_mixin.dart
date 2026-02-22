import 'package:flutter/material.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

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
      onPause();
    } else {
      repository.play();
    }
  }

  void onPause() {}

  void seek(Duration position) {
    repository.seek(position);
  }

  void seekBack() {
    final newPosition = state.position - const Duration(seconds: 10);
    final clampedPosition = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, state.duration.inMilliseconds),
    );
    repository.seek(clampedPosition);
    showDoubleTapSeekFeedback('back');
  }

  void seekForward() {
    final newPosition = state.position + const Duration(seconds: 10);
    final clampedPosition = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, state.duration.inMilliseconds),
    );
    repository.seek(clampedPosition);
    showDoubleTapSeekFeedback('forward');
  }

  void changeSpeed() {
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    final idx = speeds.indexOf(state.playbackSpeed);
    final nextSpeed = speeds[(idx + 1) % speeds.length];
    state.playbackSpeed = nextSpeed;
    repository.setSpeed(nextSpeed);
    notifyListeners();
  }

  void setSpeed(double speed) {
    state.playbackSpeed = speed;
    repository.setSpeed(speed);
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

  void toggleLock() {
    state.isLocked = !state.isLocked;
    if (state.isLocked) {
      state.showControls = false;
    }
    notifyListeners();
  }

  void unlock() {
    state.isLocked = false;
    state.showControls = true;
    notifyListeners();
    startHideTimer();
  }

  bool get isLocked => state.isLocked;

  void showControlsNow() {
    if (state.isLocked) return;
    state.showControls = true;
    notifyListeners();
    startHideTimer();
  }

  void startHideTimer() {
    Future.delayed(const Duration(seconds: 4), () {
      if (state.showControls &&
          !state.isLongPressing &&
          !state.isDraggingX &&
          !state.isDraggingY &&
          !state.isLocked) {
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

  void showDoubleTapSeekFeedback(String direction) {
    if (direction == 'back') {
      state.showDoubleTapSeekLeft = true;
    } else {
      state.showDoubleTapSeekRight = true;
    }
    notifyListeners();

    Future.delayed(const Duration(milliseconds: 400), () {
      state.showDoubleTapSeekLeft = false;
      state.showDoubleTapSeekRight = false;
      notifyListeners();
    });
  }

  void cycleAspectRatio() {
    final modes = AspectRatioMode.values;
    final currentIndex = modes.indexOf(state.aspectRatioMode);
    final nextIndex = (currentIndex + 1) % modes.length;
    state.aspectRatioMode = modes[nextIndex];
    notifyListeners();
  }

  void setAspectRatio(AspectRatioMode mode) {
    state.aspectRatioMode = mode;
    notifyListeners();
  }

  String getAspectRatioLabel(AspectRatioMode mode) {
    switch (mode) {
      case AspectRatioMode.fit:
        return 'Fit';
      case AspectRatioMode.fill:
        return 'Fill';
      case AspectRatioMode.stretch:
        return 'Stretch';
      case AspectRatioMode.ratio16x9:
        return '16:9';
      case AspectRatioMode.ratio4x3:
        return '4:3';
    }
  }

  void cycleRepeatMode() {
    final modes = RepeatMode.values;
    final currentIndex = modes.indexOf(state.repeatMode);
    final nextIndex = (currentIndex + 1) % modes.length;
    state.repeatMode = modes[nextIndex];
    notifyListeners();
  }

  void setRepeatMode(RepeatMode mode) {
    state.repeatMode = mode;
    notifyListeners();
  }

  String getRepeatModeLabel(RepeatMode mode) {
    switch (mode) {
      case RepeatMode.off:
        return 'Off';
      case RepeatMode.one:
        return 'One';
      case RepeatMode.all:
        return 'All';
    }
  }

  void loadAudioTracks() {
    final tracks = repository.getAudioTracks();
    state.audioTracks = tracks;
    notifyListeners();
  }

  void setAudioTrack(int index) {
    state.currentAudioTrackIndex = index;
    repository.setAudioTrack(index);
    notifyListeners();
  }
}
