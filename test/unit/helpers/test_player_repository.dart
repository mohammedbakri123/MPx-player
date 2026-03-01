import 'dart:async';
import 'package:mpx/features/player/domain/repositories/player_repository.dart';

/// Test implementation of [PlayerRepository] for unit tests.
///
/// This provides full control over stream emissions and method behaviors
/// without the complexity of Mockito stubbing.
class TestPlayerRepository implements PlayerRepository {
  final StreamController<bool> _playingController =
      StreamController<bool>.broadcast();
  final StreamController<Duration> _positionController =
      StreamController<Duration>.broadcast();
  final StreamController<Duration> _durationController =
      StreamController<Duration>.broadcast();
  final StreamController<bool> _bufferingController =
      StreamController<bool>.broadcast();
  final StreamController<bool> _completedController =
      StreamController<bool>.broadcast();
  final StreamController<void> _audioTracksController =
      StreamController<void>.broadcast();

  bool _isDisposed = false;
  List<AudioTrackInfo> _audioTracks = [];

  /// Emits a new playing state
  void emitPlaying(bool playing) => _playingController.add(playing);

  /// Emits a new position
  void emitPosition(Duration position) => _positionController.add(position);

  /// Emits a new duration
  void emitDuration(Duration duration) => _durationController.add(duration);

  /// Emits a new buffering state
  void emitBuffering(bool buffering) => _bufferingController.add(buffering);

  /// Emits completion state
  void emitCompleted(bool completed) => _completedController.add(completed);

  /// Sets audio tracks
  void setAudioTracks(List<AudioTrackInfo> tracks) {
    _audioTracks = tracks;
    _audioTracksController.add(null);
  }

  @override
  Stream<bool> get playingStream => _playingController.stream;

  @override
  Stream<Duration> get positionStream => _positionController.stream;

  @override
  Stream<Duration> get durationStream => _durationController.stream;

  @override
  Stream<bool> get bufferingStream => _bufferingController.stream;

  @override
  Stream<bool> get completedStream => _completedController.stream;

  @override
  Stream<void> get audioTracksStream => _audioTracksController.stream;

  @override
  List<AudioTrackInfo> getAudioTracks() => _audioTracks;

  @override
  dynamic get player => null;

  @override
  Future<void> load(String path) async {}

  @override
  Future<void> play() async {
    _playingController.add(true);
  }

  @override
  Future<void> pause() async {
    _playingController.add(false);
  }

  @override
  Future<void> seek(Duration position) async {
    _positionController.add(position);
  }

  @override
  Future<void> setSpeed(double speed) async {}

  @override
  Future<void> setVolume(double volume) async {}

  @override
  Future<void> enableSubtitles() async {}

  @override
  Future<void> disableSubtitles() async {}

  @override
  Future<void> setAudioTrack(int index) async {}

  @override
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    // Close streams - tests should handle this gracefully
    try {
      _playingController.close();
      _positionController.close();
      _durationController.close();
      _bufferingController.close();
      _completedController.close();
      _audioTracksController.close();
    } catch (e) {
      // Ignore errors from closing streams with active listeners
    }
  }
}
