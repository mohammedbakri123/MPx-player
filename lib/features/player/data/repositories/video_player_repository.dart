import 'dart:async';
import 'package:video_player/video_player.dart';
import '../../domain/repositories/player_repository.dart';

class VideoPlayerRepository implements PlayerRepository {
  VideoPlayerController? _controller;
  bool _isDisposed = false;
  final StreamController<void> _audioTracksController =
      StreamController<void>.broadcast();

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

  VideoPlayerRepository();

  VideoPlayerController? get controller => _controller;

  @override
  dynamic get player => null;

  void _listener() {
    if (_controller == null || _isDisposed) return;
    final value = _controller!.value;
    _playingController.add(value.isPlaying);
    _positionController.add(value.position);
    _durationController.add(value.duration);
    _bufferingController.add(value.isBuffering);
    _completedController.add(
        value.position >= value.duration && value.duration != Duration.zero);
  }

  @override
  Future<void> load(String path) async {
    _ensureNotDisposed();
    _controller?.dispose();
    _controller = VideoPlayerController.networkUrl(Uri.parse(path));
    _controller!.addListener(_listener);
    await _controller!.initialize();
    await _controller!.setLooping(false);
  }

  @override
  Future<void> play() async {
    _ensureNotDisposed();
    if (_controller != null) {
      await _controller!.play();
    }
  }

  @override
  Future<void> pause() async {
    _ensureNotDisposed();
    if (_controller != null) {
      await _controller!.pause();
    }
  }

  @override
  Future<void> seek(Duration position) async {
    _ensureNotDisposed();
    if (_controller != null) {
      await _controller!.seekTo(position);
    }
  }

  @override
  Future<void> setSpeed(double speed) async {
    _ensureNotDisposed();
    if (_controller != null) {
      await _controller!.setPlaybackSpeed(speed);
    }
  }

  @override
  Future<void> setVolume(double volume) async {
    _ensureNotDisposed();
    if (_controller != null) {
      await _controller!.setVolume(volume);
    }
  }

  @override
  Future<void> enableSubtitles() async {
    _ensureNotDisposed();
  }

  @override
  Future<void> disableSubtitles() async {
    _ensureNotDisposed();
  }

  @override
  List<AudioTrackInfo> getAudioTracks() {
    _ensureNotDisposed();
    return [];
  }

  @override
  Future<void> setAudioTrack(int index) async {
    _ensureNotDisposed();
  }

  @override
  Stream<bool> get playingStream {
    _ensureNotDisposed();
    return _playingController.stream;
  }

  @override
  Stream<Duration> get positionStream {
    _ensureNotDisposed();
    return _positionController.stream;
  }

  @override
  Stream<Duration> get durationStream {
    _ensureNotDisposed();
    return _durationController.stream;
  }

  @override
  Stream<bool> get bufferingStream {
    _ensureNotDisposed();
    return _bufferingController.stream;
  }

  @override
  Stream<bool> get completedStream {
    _ensureNotDisposed();
    return _completedController.stream;
  }

  @override
  Stream<void> get audioTracksStream {
    _ensureNotDisposed();
    return _audioTracksController.stream;
  }

  @override
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _controller?.removeListener(_listener);
    _audioTracksController.close();
    _playingController.close();
    _positionController.close();
    _durationController.close();
    _bufferingController.close();
    _completedController.close();
    _controller?.dispose();
    _controller = null;
  }

  void _ensureNotDisposed() {
    if (_isDisposed) {
      throw StateError(
        'VideoPlayerRepository has been disposed and cannot be used.',
      );
    }
  }
}
