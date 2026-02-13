import 'dart:async';
import 'package:media_kit/media_kit.dart';

/// Service for extracting video metadata (width, height, duration)
class VideoMetadataService {
  static final VideoMetadataService _instance =
      VideoMetadataService._internal();
  factory VideoMetadataService() => _instance;
  VideoMetadataService._internal();

  // Cache for metadata to avoid re-extracting
  final Map<String, VideoMetadata> _metadataCache = {};

  /// Extract metadata from a video file
  Future<VideoMetadata?> extractMetadata(String videoPath) async {
    // Check cache first
    if (_metadataCache.containsKey(videoPath)) {
      return _metadataCache[videoPath];
    }

    try {
      // Create a temporary player to extract metadata
      final player = Player();

      // Open the video file without playing
      await player.open(Media(videoPath), play: false);

      // Wait for the video to load and get dimensions
      final completer = Completer<VideoMetadata?>();
      int? width;
      int? height;
      Duration? duration;

      // First check if values are already available
      final currentWidth = player.state.width;
      final currentHeight = player.state.height;
      if (currentWidth != null && currentHeight != null) {
        final metadata = VideoMetadata(
          width: currentWidth,
          height: currentHeight,
          duration: player.state.duration,
        );
        _metadataCache[videoPath] = metadata;
        player.dispose();
        return metadata;
      }

      // Listen for width and height streams
      final widthSubscription = player.stream.width.listen((w) {
        width = w;
        if (width != null && height != null && !completer.isCompleted) {
          completer.complete(
              VideoMetadata(width: width, height: height, duration: duration));
        }
      });

      final heightSubscription = player.stream.height.listen((h) {
        height = h;
        if (width != null && height != null && !completer.isCompleted) {
          completer.complete(
              VideoMetadata(width: width, height: height, duration: duration));
        }
      });

      // Timeout after 5 seconds
      final timer = Timer(const Duration(seconds: 5), () {
        if (!completer.isCompleted) {
          completer.complete(null);
        }
      });

      // Wait for metadata or timeout
      final metadata = await completer.future;

      // Cancel subscriptions and dispose
      await widthSubscription.cancel();
      await heightSubscription.cancel();
      timer.cancel();
      player.dispose();

      if (metadata != null) {
        _metadataCache[videoPath] = metadata;
      }

      return metadata;
    } catch (e) {
      return null;
    }
  }
}

class VideoMetadata {
  final int? width;
  final int? height;
  final Duration? duration;

  VideoMetadata({
    this.width,
    this.height,
    this.duration,
  });
}
