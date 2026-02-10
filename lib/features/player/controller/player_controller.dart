import 'dart:ui' show Color;
import 'package:flutter/foundation.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../domain/repositories/player_repository.dart';
import '../data/repositories/media_kit_player_repository.dart';

/// Controller for managing video player state and user interactions.
///
/// This controller follows the clean architecture pattern:
/// - Depends on [PlayerRepository] abstraction (not concrete implementation)
/// - Handles high-level coordination and state management
/// - Manages gesture interactions
/// - Calculates UI-related values (volume, brightness)
/// - Provides ChangeNotifier for UI updates
///
/// **Responsibilities:**
/// - Gesture handling (seek, volume, brightness)
/// - Playback coordination
/// - Speed control logic
/// - State management (ChangeNotifier)
/// - UI indicators coordination
///
/// **Does NOT:**
/// - Directly control media engine (delegates to repository)
/// - Manage video rendering (handled by presentation layer)
class PlayerController extends ChangeNotifier {
  final PlayerRepository _repository;

  /// Returns the underlying Player instance for VideoController creation.
  ///
  /// This is only needed for video rendering in the presentation layer.
  /// Casts to MediaKitPlayerRepository to access the player getter.
  dynamic get player => (_repository as MediaKitPlayerRepository).player;

  /// Returns the VideoController for video rendering.
  ///
  /// This is created lazily and cached for the lifetime of the controller.
  late final VideoController videoController =
      VideoController((_repository as MediaKitPlayerRepository).player);

  // Playback state
  bool isPlaying = true;
  bool showControls = true;
  bool isBuffering = false;
  bool isFullscreen = true;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  double volume = 100;
  double playbackSpeed = 1.0;
  bool isLongPressing = false;

  // Subtitle state (UI-only, no subtitle track management here)
  bool subtitlesEnabled = true;
  double subtitleFontSize = 24.0;
  Color subtitleColor = const Color(0xFFFFFFFF);
  bool subtitleHasBackground = true;

  // Gesture state
  double dragStartX = 0;
  bool isDraggingX = false;
  bool isDraggingY = false;
  String verticalDragSide = '';
  Duration seekStartPosition = Duration.zero;

  // UI indicators
  bool showSeekIndicator = false;
  String seekDirection = '';
  bool showVolumeIndicator = false;
  bool showBrightnessIndicator = false;
  double brightnessValue = 0.5;

  /// Creates a PlayerController with dependency injection.
  ///
  /// [repository] - The player engine abstraction for playback control.
  PlayerController(this._repository) {
    _setupListeners();
  }

  void _setupListeners() {
    _repository.playingStream.listen((playing) {
      isPlaying = playing;
      notifyListeners();
    });

    _repository.positionStream.listen((pos) {
      if (!isDraggingX) {
        position = pos;
        notifyListeners();
      }
    });

    _repository.durationStream.listen((dur) {
      duration = dur;
      notifyListeners();
    });

    _repository.bufferingStream.listen((buffering) {
      isBuffering = buffering;
      notifyListeners();
    });
  }

  Future<void> loadVideo(String path) async {
    await _repository.load(path);
    await _repository.setVolume(volume);

    if (subtitlesEnabled) {
      await _repository.enableSubtitles();
    }
  }

  void togglePlayPause() {
    if (isPlaying) {
      _repository.pause();
    } else {
      _repository.play();
    }
  }

  void seek(Duration position) {
    _repository.seek(position);
  }

  void seekBack() {
    final newPosition = position - const Duration(seconds: 10);
    _repository.seek(newPosition);
    showSeekFeedback('back');
  }

  void seekForward() {
    final newPosition = position + const Duration(seconds: 10);
    _repository.seek(newPosition);
    showSeekFeedback('forward');
  }

  void changeSpeed() {
    final speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    final idx = speeds.indexOf(playbackSpeed);
    final nextSpeed = speeds[(idx + 1) % speeds.length];
    playbackSpeed = nextSpeed;
    _repository.setSpeed(nextSpeed);
    notifyListeners();
  }

  void setVolume(double value) {
    volume = value;
    _repository.setVolume(value);
    notifyListeners();
  }

  void setBrightness(double value) {
    brightnessValue = value.clamp(0.0, 1.0);
    notifyListeners();
  }

  void toggleFullscreen() {
    isFullscreen = !isFullscreen;
    notifyListeners();
  }

  void showControlsNow() {
    showControls = true;
    notifyListeners();
    startHideTimer();
  }

  void startHideTimer() {
    Future.delayed(const Duration(seconds: 4), () {
      if (showControls && !isLongPressing && !isDraggingX && !isDraggingY) {
        showControls = false;
        notifyListeners();
      }
    });
  }

  // Gesture handlers
  void onHorizontalDragStart(double startX) {
    dragStartX = startX;
    seekStartPosition = position;
    isDraggingX = true;
    showSeekIndicator = true;
    notifyListeners();
  }

  void onHorizontalDragUpdate(double currentX, double screenWidth) {
    final deltaX = currentX - dragStartX;
    final seekPercent = deltaX / screenWidth;
    final seekMs = (seekPercent * duration.inMilliseconds).toInt();
    final newPosition = seekStartPosition + Duration(milliseconds: seekMs);

    position = Duration(
      milliseconds:
          newPosition.inMilliseconds.clamp(0, duration.inMilliseconds),
    );
    seekDirection = deltaX > 0 ? 'forward' : 'back';
    notifyListeners();
  }

  void onHorizontalDragEnd() {
    _repository.seek(position);
    isDraggingX = false;
    showSeekIndicator = false;
    notifyListeners();
    startHideTimer();
  }

  void onVerticalDragStart(String side) {
    isDraggingY = true;
    verticalDragSide = side;
    if (side == 'left') {
      showBrightnessIndicator = true;
    } else {
      showVolumeIndicator = true;
    }
    notifyListeners();
  }

  void onVerticalDragUpdate(double delta) {
    if (verticalDragSide == 'left') {
      final brightnessUpdate = -delta / 200;
      brightnessValue = (brightnessValue + brightnessUpdate).clamp(0.0, 1.0);
    } else {
      final volumeUpdate = -delta / 200;
      volume = (volume + volumeUpdate * 100).clamp(0, 100);
      _repository.setVolume(volume);
    }
    notifyListeners();
  }

  void onVerticalDragEnd() {
    isDraggingY = false;
    showBrightnessIndicator = false;
    showVolumeIndicator = false;
    notifyListeners();
    startHideTimer();
  }

  void onLongPressStart() {
    isLongPressing = true;
    playbackSpeed = 2.0;
    _repository.setSpeed(2.0);
    notifyListeners();
  }

  void onLongPressEnd() {
    isLongPressing = false;
    playbackSpeed = 1.0;
    _repository.setSpeed(1.0);
    notifyListeners();
  }

  void showSeekFeedback(String direction) {
    showSeekIndicator = true;
    seekDirection = direction;
    notifyListeners();
    Future.delayed(const Duration(milliseconds: 500), () {
      showSeekIndicator = false;
      notifyListeners();
    });
  }

  // Subtitle methods
  void toggleSubtitles(bool value) {
    subtitlesEnabled = value;
    if (value) {
      _repository.enableSubtitles();
    } else {
      _repository.disableSubtitles();
    }
    notifyListeners();
  }

  void setSubtitleFontSize(double size) {
    subtitleFontSize = size;
    notifyListeners();
  }

  void setSubtitleColor(Color color) {
    subtitleColor = color;
    notifyListeners();
  }

  void setSubtitleBackground(bool hasBackground) {
    subtitleHasBackground = hasBackground;
    notifyListeners();
  }

  // Helper methods
  String formatTime(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final hours = duration.inHours;
    final mins = twoDigits(duration.inMinutes.remainder(60));
    final secs = twoDigits(duration.inSeconds.remainder(60));
    return hours > 0 ? '${twoDigits(hours)}:$mins:$secs' : '$mins:$secs';
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    _repository.dispose();
    super.dispose();
  }
}
