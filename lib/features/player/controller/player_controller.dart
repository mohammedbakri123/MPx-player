import 'dart:ui' show Color;
import 'package:flutter/foundation.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../domain/repositories/player_repository.dart';
import '../data/repositories/media_kit_player_repository.dart';
import 'player_state.dart';
import 'mixins/gesture_handler_mixin.dart';
import 'mixins/subtitle_manager_mixin.dart';
import 'mixins/playback_control_mixin.dart';
import 'utils/time_formatter.dart' show formatTime;

export 'player_state.dart';
export 'utils/time_formatter.dart' show formatTime;

/// Controller for managing video player state and user interactions.
///
/// This controller follows the clean architecture pattern:
/// - Depends on [PlayerRepository] abstraction (not concrete implementation)
/// - Uses mixins to organize functionality into logical groups
/// - Provides ChangeNotifier for UI updates
class PlayerController extends ChangeNotifier
    with GestureHandlerMixin, SubtitleManagerMixin, PlaybackControlMixin {
  final PlayerRepository _repository;
  final PlayerState _state = PlayerState();

  // Getters for state access
  @override
  PlayerRepository get repository => _repository;

  @override
  PlayerState get state => _state;

  // Convenience getters for UI
  bool get isPlaying => _state.isPlaying;
  bool get showControls => _state.showControls;
  bool get isBuffering => _state.isBuffering;
  bool get isFullscreen => _state.isFullscreen;
  Duration get position => _state.position;
  Duration get duration => _state.duration;
  double get volume => _state.volume;
  double get playbackSpeed => _state.playbackSpeed;
  bool get isLongPressing => _state.isLongPressing;
  bool get subtitlesEnabled => _state.subtitlesEnabled;
  double get subtitleFontSize => _state.subtitleFontSize;
  Color get subtitleColor => _state.subtitleColor;
  bool get subtitleHasBackground => _state.subtitleHasBackground;
  double get dragStartX => _state.dragStartX;
  bool get isDraggingX => _state.isDraggingX;
  bool get isDraggingY => _state.isDraggingY;
  String get verticalDragSide => _state.verticalDragSide;
  Duration get seekStartPosition => _state.seekStartPosition;
  bool get showSeekIndicator => _state.showSeekIndicator;
  String get seekDirection => _state.seekDirection;
  bool get showVolumeIndicator => _state.showVolumeIndicator;
  bool get showBrightnessIndicator => _state.showBrightnessIndicator;
  double get brightnessValue => _state.brightnessValue;

  /// Returns the underlying Player instance for VideoController creation.
  dynamic get player => (_repository as MediaKitPlayerRepository).player;

  /// Returns the VideoController for video rendering.
  late final VideoController videoController =
      VideoController((_repository as MediaKitPlayerRepository).player);

  /// Creates a PlayerController with dependency injection.
  PlayerController(this._repository) {
    initializeSubtitles();
    _setupListeners();
  }

  void _setupListeners() {
    _repository.playingStream.listen((playing) {
      _state.isPlaying = playing;
      notifyListeners();
    });

    _repository.positionStream.listen((pos) {
      if (!_state.isDraggingX) {
        _state.position = pos;
        notifyListeners();
      }
    });

    _repository.durationStream.listen((dur) {
      _state.duration = dur;
      notifyListeners();
    });

    _repository.bufferingStream.listen((buffering) {
      _state.isBuffering = buffering;
      notifyListeners();
    });
  }

  @override
  Future<void> loadVideo(String path) async {
    await super.loadVideo(path);
    await applySubtitleSettings();
  }

  // Use the formatTime utility from the utils file
  String formatDuration(Duration duration) => formatTime(duration);

  @override
  void seek(Duration position) {
    _state.position = position;
    super.seek(position);
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    _repository.dispose();
    super.dispose();
  }
}
