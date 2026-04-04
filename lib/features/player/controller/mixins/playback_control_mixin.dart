import 'package:flutter/material.dart';
import '../../../history/services/history_service.dart';
import '../../../library/domain/entities/video_file.dart';
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
    registerControlsInteraction();

    if (state.isPlaying) {
      repository.pause();
      state.showControls = true;
      cancelHideTimer();
      notifyListeners();
      onPause();
    } else {
      repository.play();
      showControlsNow();
    }
  }

  void onPause() {}

  void seek(Duration position) {
    repository.seek(position);
  }

  void seekBack() {
    registerControlsInteraction();
    final newPosition =
        state.position - Duration(seconds: state.doubleTapSeekStep);
    final clampedPosition = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, state.duration.inMilliseconds),
    );
    repository.seek(clampedPosition);
    showDoubleTapSeekFeedback('back');
  }

  void seekForward() {
    registerControlsInteraction();
    final newPosition =
        state.position + Duration(seconds: state.doubleTapSeekStep);
    final clampedPosition = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, state.duration.inMilliseconds),
    );
    repository.seek(clampedPosition);
    showDoubleTapSeekFeedback('forward');
  }

  void changeSpeed() {
    registerControlsInteraction();
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    final idx = speeds.indexOf(state.playbackSpeed);
    final nextSpeed = speeds[(idx + 1) % speeds.length];
    state.playbackSpeed = nextSpeed;
    repository.setSpeed(nextSpeed);
    notifyListeners();
  }

  void setSpeed(double speed) {
    registerControlsInteraction();
    state.playbackSpeed = speed;
    repository.setSpeed(speed);
    notifyListeners();
  }

  void setVolume(double value) {
    registerControlsInteraction();
    state.volume = value;
    repository.setVolume(value);
    notifyListeners();
  }

  void setBrightness(double value) {
    registerControlsInteraction();
    state.brightnessValue = value.clamp(0.0, 1.0);
    notifyListeners();
  }

  void toggleFullscreen() {
    registerControlsInteraction();
    state.isFullscreen = !state.isFullscreen;
    notifyListeners();
  }

  void toggleLock() {
    registerControlsInteraction();
    state.isLocked = !state.isLocked;
    if (state.isLocked) {
      state.showControls = false;
      cancelHideTimer();
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

  void registerControlsInteraction() {
    if (state.isLocked) return;

    final shouldNotify = !state.showControls;
    state.showControls = true;
    cancelHideTimer();
    if (shouldNotify) {
      notifyListeners();
    }
    startHideTimer();
  }

  void beginControlsInteraction() {
    if (state.isLocked) return;

    state.controlsInteractionCount++;
    state.showControls = true;
    cancelHideTimer();
    notifyListeners();
  }

  void endControlsInteraction() {
    if (state.controlsInteractionCount > 0) {
      state.controlsInteractionCount--;
    }
    if (!state.isLocked) {
      startHideTimer();
    }
  }

  void toggleControlsVisibility() {
    if (state.isLocked) return;

    state.showControls = !state.showControls;
    notifyListeners();

    if (state.showControls) {
      startHideTimer();
    } else {
      cancelHideTimer();
    }
  }

  void cancelHideTimer() {
    state.hideControlsRequestId++;
  }

  void startHideTimer() {
    final requestId = ++state.hideControlsRequestId;

    Future.delayed(const Duration(seconds: 4), () {
      if (requestId != state.hideControlsRequestId) return;

      if (state.showControls &&
          state.isPlaying &&
          !state.isBuffering &&
          !state.isLongPressing &&
          !state.isDraggingX &&
          !state.isDraggingY &&
          state.controlsInteractionCount == 0 &&
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
    const modes = AspectRatioMode.values;
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
    const modes = RepeatMode.values;
    final currentIndex = modes.indexOf(state.repeatMode);
    final nextIndex = (currentIndex + 1) % modes.length;
    state.repeatMode = modes[nextIndex];
    notifyListeners();
  }

  void setRepeatMode(RepeatMode mode) {
    state.repeatMode = mode;
    notifyListeners();
  }

  void setDoubleTapSeekStep(int seconds) {
    state.doubleTapSeekStep = seconds;
    notifyListeners();
  }

  void setDragSeekSensitivity(double sensitivity) {
    state.dragSeekSensitivity = sensitivity;
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

  Future<void> loadAudioTracksWithRestore({VideoFile? video}) async {
    final tracks = repository.getAudioTracks();
    state.audioTracks = tracks;
    if (tracks.isNotEmpty && video != null) {
      final savedTrack = await HistoryService.getSelectedAudioTrack(video.id);
      if (savedTrack != null && savedTrack.isNotEmpty) {
        final idx = tracks.indexWhere((t) {
          final title = t.title?.trim();
          final lang = t.language?.trim();
          final label = title != null && title.isNotEmpty
              ? title
              : lang != null && lang.isNotEmpty
                  ? lang.toUpperCase()
                  : null;
          return label == savedTrack;
        });
        if (idx >= 0) {
          state.currentAudioTrackIndex = idx;
          repository.setAudioTrack(idx);
        }
      }
    }
    notifyListeners();
  }

  Future<void> setAudioTrack(int index, {VideoFile? video}) async {
    registerControlsInteraction();
    state.currentAudioTrackIndex = index;
    repository.setAudioTrack(index);
    if (video != null && index >= 0 && index < state.audioTracks.length) {
      final track = state.audioTracks[index];
      final title = track.title?.trim();
      final lang = track.language?.trim();
      final label = title != null && title.isNotEmpty
          ? title
          : lang != null && lang.isNotEmpty
              ? lang.toUpperCase()
              : 'Track ${index + 1}';
      await HistoryService.saveSelectedAudioTrack(video.id, label);
    }
    notifyListeners();
  }

  void previewSeek(Duration position) {
    registerControlsInteraction();
    state.position = position;
    notifyListeners();
  }
}
