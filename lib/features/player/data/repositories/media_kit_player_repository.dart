import 'package:media_kit/media_kit.dart';
import '../../domain/repositories/player_repository.dart';

/// Concrete implementation of [PlayerRepository] using media_kit Player.
///
/// This class wraps the media_kit [Player] engine and maps its API
/// to the clean repository interface defined in the domain layer.
///
/// **Architecture Note:**
/// - This class belongs to the data layer
/// - It completely wraps media_kit Player
/// - All playback control goes through repository interface methods
/// - Streams are directly mapped from media_kit to repository interface
///
/// **VideoController Note:**
/// The underlying [Player] is exposed via [player] getter specifically
/// for creating VideoController in the presentation layer. This is the
/// only media_kit type that intentionally leaks from this implementation,
/// as VideoController is required for video rendering in Flutter widgets.
class MediaKitPlayerRepository implements PlayerRepository {
  late final Player _player;
  bool _isDisposed = false;

  MediaKitPlayerRepository() {
    _player = Player();
  }

  /// Returns the underlying media_kit Player instance.
  ///
  /// **Usage:** This should ONLY be used by the presentation layer to
  /// create a VideoController for video rendering. All playback control
  /// should go through the repository interface methods, not directly
  /// through this Player instance.
  ///
  /// Example:
  /// ```dart
  /// final repository = MediaKitPlayerRepository();
  /// final videoController = VideoController(repository.player);
  /// ```
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
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _player.dispose();
  }

  /// Ensures the repository hasn't been disposed before operations.
  void _ensureNotDisposed() {
    if (_isDisposed) {
      throw StateError(
        'MediaKitPlayerRepository has been disposed and cannot be used.',
      );
    }
  }
}
