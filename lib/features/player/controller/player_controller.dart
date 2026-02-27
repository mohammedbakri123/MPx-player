import 'dart:async';
import 'dart:ui' show Color;
import 'package:flutter/foundation.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../history/services/history_service.dart';
import '../../library/domain/entities/video_file.dart';
import '../domain/repositories/player_repository.dart';
import 'player_state.dart';
import 'mixins/gesture_handler_mixin.dart';
import 'mixins/subtitle_manager_mixin.dart';
import 'mixins/playback_control_mixin.dart';
import 'utils/time_formatter.dart' show formatTime;

export 'player_state.dart';
export 'utils/time_formatter.dart' show formatTime;
export '../domain/repositories/player_repository.dart' show AudioTrackInfo;

/// Controller for managing video player state and user interactions.
///
/// This controller follows the clean architecture pattern:
/// - Depends on [PlayerRepository] abstraction (not concrete implementation)
/// - Uses mixins to organize functionality into logical groups
/// - Provides ChangeNotifier for UI updates
/// - Handles auto-saving playback position with throttling to prevent excessive saves
/// - Manages current video and exposes it for history saving
/// - Provides utility methods for formatting time and accessing player state
/// This controller is designed to be used with a Provider in the UI layer, allowing for easy state management and separation of concerns.
/// The UI layer can call methods on this controller to perform actions (play, pause, seek, etc.) and listen to state changes for updating the UI accordingly.
/// Example usage in UI layer:
/// ```dart
/// final controller = context.watch<PlayerController>();
/// // Access state
/// final isPlaying = controller.isPlaying;
/// // Call actions
/// controller.pauseVideo();
/// ```
/// The controller also handles lifecycle events such as pausing and going to background to ensure that playback position is saved appropriately.
/// The auto-save mechanism ensures that the user's playback position is periodically saved to history without overwhelming the storage with too many writes, using a throttling mechanism to limit saves to at most once every 5 seconds (unless forced).
/// Overall, this controller serves as the central hub for managing all aspects of video playback, user interactions, and state management in a clean and organized manner.
/// Note: The actual video playback is handled by the PlayerRepository implementation (e.g., MediaKitPlayerRepository), and this controller interacts with it through the defined abstraction, allowing for flexibility in changing the underlying player implementation without affecting the controller logic.
/// The controller also integrates subtitle management and gesture handling through mixins, keeping the code modular and maintainable.
/// The controller ensures that resources are properly cleaned up on disposal, including cancelling timers and disabling wakelock, to prevent memory leaks and unintended behavior when the player is no longer in use.
/// The controller is designed to be robust and user-friendly, providing a seamless video playback experience while also ensuring that user progress is saved and can be resumed later through the history service.
/// This implementation focuses on the core functionality of the player controller, and additional features such as error handling, network status monitoring, and advanced subtitle features can be added in the future as needed.
/// Overall, this PlayerController serves as a comprehensive and well-structured component for managing video playback in a Flutter application, adhering to clean architecture principles and providing a solid foundation for building a feature-rich video player experience.
///
class PlayerController extends ChangeNotifier
    with GestureHandlerMixin, SubtitleManagerMixin, PlaybackControlMixin {
  final PlayerRepository _repository;
  final PlayerState _state = PlayerState();

  // Current video being played
  VideoFile? _currentVideo;

  // Auto-save timer for periodic position saves
  Timer? _autoSaveTimer;

  // Last save timestamp for throttling
  DateTime? _lastSaveTime;

  // Throttle duration (minimum time between saves)
  static const Duration _saveThrottleDuration = Duration(seconds: 5);

  // Auto-save interval
  static const Duration _autoSaveInterval = Duration(seconds: 30);

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
  @override
  bool get isLocked => _state.isLocked;
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
  bool get showDoubleTapSeekLeft => _state.showDoubleTapSeekLeft;
  bool get showDoubleTapSeekRight => _state.showDoubleTapSeekRight;
  AspectRatioMode get aspectRatioMode => _state.aspectRatioMode;
  RepeatMode get repeatMode => _state.repeatMode;
  List<AudioTrackInfo> get audioTracks => _state.audioTracks;
  int get currentAudioTrackIndex => _state.currentAudioTrackIndex;

  /// Returns the current video being played
  VideoFile? get currentVideo => _currentVideo;

  /// Returns the underlying Player instance for VideoController creation.
  dynamic get player => _repository.player;

  /// Returns the VideoController for video rendering.
  VideoController? get videoController =>
      _repository.player != null ? VideoController(_repository.player) : null;

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

    _repository.completedStream.listen((completed) {
      if (completed) {
        // Video playback completed - reset position to 0
        resetPositionOnVideoEnd();
      }
    });
  }

  /// Load a video file and start auto-save timer
  Future<void> loadVideoFile(VideoFile video) async {
    _currentVideo = video;
    await super.loadVideo(video.path);
    await applySubtitleSettings();
    loadAudioTracks();
    _startAutoSaveTimer();
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

  /// Start the auto-save timer that saves position every 30 seconds
  void _startAutoSaveTimer() {
    _autoSaveTimer?.cancel();
    _autoSaveTimer = Timer.periodic(_autoSaveInterval, (_) {
      if (_state.isPlaying) {
        saveCurrentPosition();
      }
    });
  }

  /// Save current playback position to history
  /// Returns true if save was performed, false if throttled
  Future<bool> saveCurrentPosition({bool force = false}) async {
    // Check if we have a current video
    if (_currentVideo == null) {
      return false;
    }

    // Check if position is greater than 5 seconds (don't save at very beginning)
    if (_state.position.inSeconds < 5) {
      return false;
    }

    // Check throttling - don't save more than once every 5 seconds (unless forced)
    if (!force && _lastSaveTime != null) {
      final timeSinceLastSave = DateTime.now().difference(_lastSaveTime!);
      if (timeSinceLastSave < _saveThrottleDuration) {
        return false;
      }
    }

    // Perform the save
    _lastSaveTime = DateTime.now();
    await HistoryService.recordPlayback(
      video: _currentVideo!,
      position: _state.position,
      duration: _state.duration,
    );
    return true;
  }

  /// Save position when user pauses (always force — user explicitly paused)
  Future<void> savePositionOnPause() {
    return saveCurrentPosition(force: true);
  }

  /// Save position when app goes to background (always force — may not get another chance)
  Future<void> savePositionOnBackground() {
    return saveCurrentPosition(force: true);
  }

  @override
  void onPause() {
    savePositionOnPause();
  }

  /// Pause the video playback
  void pauseVideo() {
    _repository.pause();
    _state.isPlaying = false;
    notifyListeners();
  }

  /// Reset position to 0 when video ends (video finished)
  Future<void> resetPositionOnVideoEnd() async {
    if (_currentVideo == null) return;

    await HistoryService.recordPlayback(
      video: _currentVideo!,
      position: Duration.zero,
      duration: _state.duration,
    );
  }

  /// Pre-dispose: await the final position save BEFORE the controller is disposed.
  /// Call this from the UI layer before Navigator.pop() triggers dispose().
  Future<void> saveAndCleanup() async {
    _autoSaveTimer?.cancel();
    _autoSaveTimer = null;
    await saveCurrentPosition(force: true);
  }

  @override
  void dispose() {
    // Timer should already be cancelled by saveAndCleanup(),
    // but cancel again as a safety net.
    _autoSaveTimer?.cancel();
    _autoSaveTimer = null;

    WakelockPlus.disable();
    _repository.dispose();
    super.dispose();
  }
}
