import 'dart:async';
import 'package:media_kit/media_kit.dart';
import '../../domain/repositories/player_repository.dart';

class MediaKitPlayerRepository implements PlayerRepository {
  late final Player _player;
  bool _isDisposed = false;
  final StreamController<void> _audioTracksController =
      StreamController<void>.broadcast();

  MediaKitPlayerRepository() {
    _player = Player();
  }

  Player get player => _player;

  @override
  Future<void> load(String path) async {
    _ensureNotDisposed();
    await _player.open(Media(path));
  }

  @override
  Future<void> play() async {
    _ensureNotDisposed();
    await _player.play();
  }

  @override
  Future<void> pause() async {
    _ensureNotDisposed();
    await _player.pause();
  }

  @override
  Future<void> seek(Duration position) async {
    _ensureNotDisposed();
    await _player.seek(position);
  }

  @override
  Future<void> setSpeed(double speed) async {
    _ensureNotDisposed();
    await _player.setRate(speed);
  }

  @override
  Future<void> setVolume(double volume) async {
    _ensureNotDisposed();
    await _player.setVolume(volume);
  }

  @override
  Future<void> enableSubtitles() async {
    _ensureNotDisposed();
    await _player.setSubtitleTrack(SubtitleTrack.auto());
  }

  @override
  Future<void> disableSubtitles() async {
    _ensureNotDisposed();
    await _player.setSubtitleTrack(SubtitleTrack.no());
  }

  @override
  List<AudioTrackInfo> getAudioTracks() {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.audio;
    return tracks.asMap().entries.map((entry) {
      final track = entry.value;
      return AudioTrackInfo(
        id: entry.key,
        title: track.title,
        language: track.language,
      );
    }).toList();
  }

  @override
  Future<void> setAudioTrack(int index) async {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.audio;
    if (index >= 0 && index < tracks.length) {
      await _player.setAudioTrack(tracks[index]);
      _audioTracksController.add(null);
    }
  }

  @override
  Stream<bool> get playingStream {
    _ensureNotDisposed();
    return _player.stream.playing;
  }

  @override
  Stream<Duration> get positionStream {
    _ensureNotDisposed();
    return _player.stream.position;
  }

  @override
  Stream<Duration> get durationStream {
    _ensureNotDisposed();
    return _player.stream.duration;
  }

  @override
  Stream<bool> get bufferingStream {
    _ensureNotDisposed();
    return _player.stream.buffering;
  }

  @override
  Stream<bool> get completedStream {
    _ensureNotDisposed();
    return _player.stream.completed;
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
    _audioTracksController.close();
    _player.dispose();
  }

  void _ensureNotDisposed() {
    if (_isDisposed) {
      throw StateError(
        'MediaKitPlayerRepository has been disposed and cannot be used.',
      );
    }
  }
}
