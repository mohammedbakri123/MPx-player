import 'dart:async';
import 'dart:io' as dartIo;
import 'dart:ui' show Color, FontWeight;
import 'package:flutter/foundation.dart';
import 'package:flutter_mpv_video/flutter_mpv_video.dart';
import 'package:mpx/features/player/controller/mixins/volume_manager_mixin.dart';
import 'package:mpx/features/player/controller/mixins/brightness_manager_mixin.dart';
import 'package:mpx/features/player/controller/mixins/gesture_coordinator_mixin.dart'
    show GestureCoordinatorMixin;
import 'package:mpx/features/player/presentation/widgets/gesture_layer.dart'
    show SeekDirection;
import 'package:mpx/features/settings/services/app_settings_service.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../core/services/logger_service.dart';
import '../../history/services/history_service.dart';
import '../../library/domain/entities/video_file.dart';
import '../domain/repositories/player_repository.dart';
import '../data/repositories/mpv_player_repository.dart';
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
class PlayerController extends ChangeNotifier
    with
        GestureCoordinatorMixin,
        GestureHandlerMixin,
        SubtitleManagerMixin,
        PlaybackControlMixin,
        VolumeManagerMixin,
        BrightnessManagerMixin {
  final PlayerRepository _repository;
  final PlayerState _state = PlayerState();

  // Current video being played
  VideoFile? _currentVideo;

  // Auto-save timer for periodic position saves

  Timer? _autoSaveTimer;

  // Double-tap gesture tracking
  int _tapCount = 0;
  DateTime? _lastTapTime;
  Timer? _tapTimer;
  static const _doubleTapTimeout = Duration(milliseconds: 200);

  // Last save timestamp for throttling
  DateTime? _lastSaveTime;

  // Throttle duration (minimum time between saves)
  static const Duration _saveThrottleDuration = Duration(seconds: 5);

  // Auto-save interval
  static const Duration _autoSaveInterval = Duration(seconds: 30);
  DateTime? _lastPositionUiUpdate;

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
  @override
  double get volume => _state.volume;
  double get playbackSpeed => _state.playbackSpeed;
  bool get isLongPressing => _state.isLongPressing;
  @override
  bool get isLocked => _state.isLocked;
  bool get subtitlesEnabled => _state.subtitlesEnabled;
  double get subtitleFontSize => _state.subtitleFontSize;
  Color get subtitleColor => _state.subtitleColor;
  String get subtitleFontFamily => _state.subtitleFontFamily;
  bool get subtitleHasBackground => _state.subtitleHasBackground;
  FontWeight get subtitleFontWeight => _state.subtitleFontWeight;
  double get subtitleBottomPadding => _state.subtitleBottomPadding;
  double get subtitleBackgroundOpacity => _state.subtitleBackgroundOpacity;
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
  List<SubtitleTrackInfo> get subtitleTracks => _state.subtitleTracks;
  int get currentSubtitleTrackIndex => _state.currentSubtitleTrackIndex;

  /// Returns the current video being played
  VideoFile? get currentVideo => _currentVideo;

  int get doubleTapSeekStep => _state.doubleTapSeekStep;
  double get dragSeekSensitivity => _state.dragSeekSensitivity;

  @override
  Future<void> loadExternalSubtitle(String path, {VideoFile? video}) async {
    final targetVideo = video ?? _currentVideo;
    if (targetVideo == null) return;
    await super.loadExternalSubtitle(path, video: targetVideo);
  }

  @override
  Future<void> setSubtitleTrack(int index, {VideoFile? video}) async {
    final targetVideo = video ?? _currentVideo;
    await super.setSubtitleTrack(index, video: targetVideo);
  }

  @override
  Future<void> setAudioTrack(int index, {VideoFile? video}) async {
    final targetVideo = video ?? _currentVideo;
    await super.setAudioTrack(index, video: targetVideo);
  }

  @override
  Future<void> setDoubleTapSeekStep(int seconds) async {
    super.setDoubleTapSeekStep(seconds);
    await AppSettingsService.setDoubleTapSeekStep(seconds);
  }

  @override
  Future<void> setDragSeekSensitivity(double sensitivity) async {
    super.setDragSeekSensitivity(sensitivity);
    await AppSettingsService.setDragSeekSensitivity(sensitivity);
  }

  /// Returns the underlying Player instance for VideoController creation.
  dynamic get player => (_repository as MpvPlayerRepository).player;

  /// Returns the VideoController for video rendering.
  late final VideoController videoController =
      VideoController((_repository as MpvPlayerRepository).player);

  /// Creates a PlayerController with dependency injection.
  PlayerController(this._repository) {
    // Setup gesture coordination between mixins
    setupGestureCoordination(
      onSeekStart: startSeek,
      onSeekEnd: endSeek,
      onVolumeAdjustStart: startVolumeAdjust,
      onVolumeAdjustEnd: endVolumeAdjust,
      onBrightnessAdjustStart: startBrightnessAdjust,
      onBrightnessAdjustEnd: endBrightnessAdjust,
      shouldProcessVerticalDrag: shouldProcessVerticalDrag,
      shouldProcessLongPress: shouldProcessLongPress,
    );

    initializeSubtitles();
    initializeVolume();
    initializeBrightness();
    _loadSeekSettings();
    _setupListeners();
  }

  void _loadSeekSettings() {
    state.doubleTapSeekStep = AppSettingsService.doubleTapSeekStep;
    state.dragSeekSensitivity = AppSettingsService.dragSeekSensitivity;
  }

  void _setupListeners() {
    _repository.playingStream.listen((playing) {
      _state.isPlaying = playing;
      notifyListeners();
    });

    _repository.positionStream.listen((pos) {
      if (_state.isDraggingX) return;

      _state.position = pos;
      final now = DateTime.now();
      final shouldNotify = _state.showControls ||
          _state.showSeekIndicator ||
          _state.showVolumeIndicator ||
          _state.showBrightnessIndicator ||
          _state.showDoubleTapSeekLeft ||
          _state.showDoubleTapSeekRight ||
          _state.controlsInteractionCount > 0 ||
          _lastPositionUiUpdate == null ||
          now.difference(_lastPositionUiUpdate!) >=
              const Duration(milliseconds: 250);

      if (shouldNotify) {
        _lastPositionUiUpdate = now;
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

    _repository.subtitleTracksStream.listen((_) {
      loadSubtitleTracks();
    });

    _repository.audioTracksStream.listen((_) {
      Future.delayed(const Duration(milliseconds: 300), () {
        loadAudioTracks();
      });
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
    await loadVideo(video.path);
    await _applyPlayerPreset();
    loadAudioTracks();
    loadSubtitleTracks();
    await applySubtitleSettings();
    await Future.delayed(const Duration(milliseconds: 500));
    await _loadSidecarSubtitles();
    await _loadPersistedSubtitles();
    await loadSubtitleTracksWithRestore(video: video);
    await loadAudioTracksWithRestore(video: video);
    _startAutoSaveTimer();
  }

  Future<void> _loadSidecarSubtitles() async {
    if (_currentVideo == null) return;
    final videoPath = _currentVideo!.path;
    final dir = videoPath.substring(0, videoPath.lastIndexOf('/'));
    final baseName = videoPath.split('/').last;
    final dotIndex = baseName.lastIndexOf('.');
    final nameWithoutExt =
        dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
    const extensions = ['.srt', '.ass', '.ssa', '.sub', '.vtt', '.idx', '.smi'];
    final exactMatches = <String>[];
    final anyMatches = <String>[];
    for (final ext in extensions) {
      final exactPath = '$dir/$nameWithoutExt$ext';
      if (await _fileExists(exactPath)) {
        exactMatches.add(exactPath);
      }
    }
    try {
      final dirContents = await dartIo.Directory(dir).list().toList();
      for (final entity in dirContents) {
        if (entity is dartIo.File) {
          final lowerPath = entity.path.toLowerCase();
          for (final ext in extensions) {
            if (lowerPath.endsWith(ext)) {
              if (!exactMatches.contains(entity.path)) {
                anyMatches.add(entity.path);
              }
              break;
            }
          }
        }
      }
    } catch (_) {}
    final allSubPaths = [...exactMatches, ...anyMatches];
    for (final subPath in allSubPaths) {
      try {
        await _repository.loadExternalSubtitle(subPath);
        AppLogger.d('Loaded sidecar subtitle: $subPath');
      } catch (e) {
        AppLogger.e('Failed to load sidecar subtitle: $subPath, error: $e');
      }
    }
    if (allSubPaths.isNotEmpty) {
      await _persistSubtitlePaths(allSubPaths);
    }
    loadSubtitleTracks();
    AppLogger.d(
        'Total subtitle tracks after sidecar load: ${state.subtitleTracks.length}');
    notifyListeners();
  }

  Future<void> _loadPersistedSubtitles() async {
    if (_currentVideo == null) return;
    try {
      final paths = await HistoryService.getSubtitlePaths(_currentVideo!.id);
      AppLogger.d('Persisted subtitle paths for ${_currentVideo!.id}: $paths');
      for (final path in paths) {
        if (await _fileExists(path)) {
          try {
            await _repository.loadExternalSubtitle(path);
            AppLogger.d('Loaded persisted subtitle: $path');
          } catch (e) {
            AppLogger.e('Failed to load persisted subtitle: $path, error: $e');
          }
        } else {
          AppLogger.w('Persisted subtitle file not found: $path');
        }
      }
      loadSubtitleTracks();
      AppLogger.d(
          'Total subtitle tracks after persisted load: ${state.subtitleTracks.length}');
      notifyListeners();
    } catch (e) {
      AppLogger.e('Failed to load persisted subtitles: $e');
    }
  }

  Future<void> _persistSubtitlePaths(List<String> paths) async {
    if (_currentVideo == null) return;
    try {
      final existing = await HistoryService.getSubtitlePaths(_currentVideo!.id);
      final merged = {...existing, ...paths}.toList();
      await HistoryService.saveSubtitlePaths(_currentVideo!.id, merged);
    } catch (_) {}
  }

  Future<bool> _fileExists(String path) async {
    try {
      return await dartIo.File(path).exists();
    } catch (_) {
      return false;
    }
  }

  Future<void> _applyPlayerPreset() async {
    _state.playbackSpeed = AppSettingsService.presetPlaybackSpeed;
    _state.aspectRatioMode = AppSettingsService.presetAspectRatioMode;
    _state.repeatMode = AppSettingsService.presetRepeatMode;
    await _repository.setSpeed(_state.playbackSpeed);
    notifyListeners();
  }

  Future<void> applyVideoPerformanceProfile() async {
    await (_repository as MpvPlayerRepository)
        .applyVideoPerformanceConfiguration(
      AppSettingsService.videoPerformanceConfiguration,
    );
  }

  Future<void> syncKeepScreenAwakePreference() async {
    if (AppSettingsService.keepScreenAwake) {
      await WakelockPlus.enable();
      return;
    }

    await WakelockPlus.disable();
  }

  // @override
  // Future<void> loadVideo(String path) async {
  //   await super.loadVideo(path);
  //   await applySubtitleSettings();
  // }

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

  /// Handle tap gesture for double-tap seek detection
  void handleDoubleTap(SeekDirection direction) {
    // Block double-tap during seek or volume/brightness adjustment
    if (!shouldProcessDoubleTap()) return;

    final now = DateTime.now();

    if (_lastTapTime != null &&
        now.difference(_lastTapTime!) < _doubleTapTimeout) {
      _tapCount++;
      if (_tapCount == 2) {
        // Double tap detected - seek based on direction
        if (direction == SeekDirection.back) {
          seekBack();
        } else {
          seekForward();
        }
        _resetTapCounter();
        return;
      }
    } else {
      _tapCount = 1;
    }

    _lastTapTime = now;

    // Wait for possible second tap
    _tapTimer?.cancel();
    _tapTimer = Timer(_doubleTapTimeout, () {
      if (_tapCount == 1) {
        // Single tap on side zones - show controls
        showControlsNow();
      }
      _resetTapCounter();
    });
  }

  /// Handle center tap for double-tap pause/play
  void handleCenterTap() {
    // Block during seek or volume/brightness adjustment
    if (!shouldProcessDoubleTap()) return;

    final now = DateTime.now();

    if (_lastTapTime != null &&
        now.difference(_lastTapTime!) < _doubleTapTimeout) {
      _tapCount++;
      if (_tapCount == 2) {
        // Double tap detected - toggle pause/play
        togglePlayPause();
        _resetTapCounter();
        return;
      }
    } else {
      _tapCount = 1;
    }

    _lastTapTime = now;

    // Wait for possible second tap
    _tapTimer?.cancel();
    _tapTimer = Timer(_doubleTapTimeout, () {
      toggleControlsVisibility();
      _resetTapCounter();
    });
  }

  void _resetTapCounter() {
    _tapCount = 0;
    _lastTapTime = null;
    _tapTimer?.cancel();
    _tapTimer = null;
  }

  @override
  void dispose() {
    // Timer should already be cancelled by saveAndCleanup(),
    // but cancel again as a safety net.
    _autoSaveTimer?.cancel();
    _autoSaveTimer = null;

    // Cancel double-tap timer
    _tapTimer?.cancel();
    _tapTimer = null;

    unawaited(WakelockPlus.disable().catchError((_) {}));
    _repository.dispose();
    super.dispose();
  }
}
