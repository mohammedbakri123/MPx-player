import 'dart:io';
import 'package:media_data_extractor/media_data_extractor.dart';
import 'logger_service.dart';

/// Service for extracting video metadata (width, height, duration)
/// Uses media_data_extractor for reliable cross-platform metadata extraction
class VideoMetadataService {
  static final VideoMetadataService _instance =
      VideoMetadataService._internal();
  factory VideoMetadataService() => _instance;
  VideoMetadataService._internal();

  final _extractor = MediaDataExtractor();

  // Cache for metadata to avoid re-extracting
  final Map<String, VideoMetadata> _metadataCache = {};

  /// Maximum cache size to prevent memory issues
  static const int _maxCacheSize = 1000;

  /// Extract metadata from a video file
  Future<VideoMetadata?> extractMetadata(String videoPath) async {
    // Check cache first
    if (_metadataCache.containsKey(videoPath)) {
      AppLogger.i('Returning cached metadata for: $videoPath');
      // Move to end (mark as recently used)
      final metadata = _metadataCache.remove(videoPath);
      _metadataCache[videoPath] = metadata!;
      return metadata;
    }

    // Verify file exists
    if (!File(videoPath).existsSync()) {
      AppLogger.w('Video file does not exist: $videoPath');
      return null;
    }

    try {
      AppLogger.i('Calling media_data_extractor for: $videoPath');

      final videoData = await _extractor.getVideoData(
        MediaDataSource(
          type: MediaDataSourceType.file,
          url: videoPath,
        ),
      );

      if (videoData.tracks.isEmpty) {
        AppLogger.w('media_data_extractor returned no tracks for: $videoPath');
        return null;
      }

      // Get the first video track
      final track = videoData.tracks.first;
      if (track == null) {
        AppLogger.w('First track is null for: $videoPath');
        return null;
      }

      AppLogger.i(
          'Extracted metadata - width: ${track.width}, height: ${track.height}, duration: ${track.duration}');

      final result = VideoMetadata(
        width: track.width?.toInt(),
        height: track.height?.toInt(),
        duration: track.duration != null
            ? Duration(milliseconds: track.duration!.toInt())
            : null,
      );

      // Add to cache with eviction
      if (_metadataCache.length >= _maxCacheSize) {
        // Remove oldest entry (first item)
        final oldestKey = _metadataCache.keys.first;
        _metadataCache.remove(oldestKey);
        AppLogger.d('Evicted metadata cache entry: $oldestKey');
      }

      _metadataCache[videoPath] = result;
      return result;
    } catch (e, stackTrace) {
      AppLogger.e('Error extracting metadata: $e');
      AppLogger.e('Stack trace: $stackTrace');
      return null;
    }
  }

  /// Clear the metadata cache
  void clearCache() {
    _metadataCache.clear();
    AppLogger.i('Metadata cache cleared');
  }

  /// Get cache size
  int get cacheSize => _metadataCache.length;

  /// Get cache stats
  Map<String, dynamic> get stats => {
    'cacheSize': _metadataCache.length,
    'maxCacheSize': _maxCacheSize,
  };
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
