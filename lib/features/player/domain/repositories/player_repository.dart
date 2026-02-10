/// Abstract repository interface for video player control.
///
/// This interface defines the contract for video playback operations
/// without depending on any specific implementation (e.g., media_kit).
///
/// The domain layer should depend on this abstraction, not concrete implementations.
abstract class PlayerRepository {
  /// Loads a video from the given file path.
  ///
  /// [path] - The absolute file path to the video file.
  /// Throws an exception if the video cannot be loaded.
  Future<void> load(String path);

  /// Starts or resumes video playback.
  Future<void> play();

  /// Pauses video playback.
  Future<void> pause();

  /// Seeks to a specific position in the video.
  ///
  /// [position] - The target position in the video timeline.
  Future<void> seek(Duration position);

  /// Sets the playback speed.
  ///
  /// [speed] - The playback rate (e.g., 1.0 for normal, 2.0 for double speed).
  Future<void> setSpeed(double speed);

  /// Sets the volume level.
  ///
  /// [volume] - Volume level from 0.0 (mute) to 100.0 (max).
  Future<void> setVolume(double volume);

  /// Enables subtitles with auto-detection.
  Future<void> enableSubtitles();

  /// Disables all subtitles.
  Future<void> disableSubtitles();

  /// Stream of playing state.
  ///
  /// Emits `true` when video is playing, `false` when paused.
  Stream<bool> get playingStream;

  /// Stream of current playback position.
  ///
  /// Continuously emits the current position as the video plays.
  Stream<Duration> get positionStream;

  /// Stream of total video duration.
  ///
  /// Emits the total duration once the video metadata is loaded.
  Stream<Duration> get durationStream;

  /// Stream of buffering state.
  ///
  /// Emits `true` when video is buffering, `false` otherwise.
  Stream<bool> get bufferingStream;

  /// Disposes of all resources used by the player.
  ///
  /// Should be called when the player is no longer needed.
  void dispose();
}
