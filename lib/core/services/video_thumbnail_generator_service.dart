import 'dart:io';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path_provider/path_provider.dart';
import '../utils/lru_cache.dart';
import 'logger_service.dart';

/// Service for generating video thumbnails using native platform APIs
/// Android: MediaMetadataRetriever
/// iOS: AVAssetImageGenerator
class VideoThumbnailGeneratorService {
  static final VideoThumbnailGeneratorService _instance =
      VideoThumbnailGeneratorService._internal();
  factory VideoThumbnailGeneratorService() => _instance;
  VideoThumbnailGeneratorService._internal();

  // LRU Cache with 200 item limit
  final LRUCache<String, String> _thumbnailPathCache =
      LRUCache<String, String>(200);
  final Set<String> _pendingRequests = {};

  /// Generate a thumbnail for a video file
  /// Returns the file path to the generated thumbnail
  Future<String?> generateThumbnail(String videoPath,
      {int seekMs = 1000}) async {
    // Check cache first
    final cachedPath = _thumbnailPathCache.get(videoPath);
    if (cachedPath != null) {
      final exists = await File(cachedPath).exists();
      if (exists) {
        return cachedPath;
      }
    }

    // Check if already being generated
    if (_pendingRequests.contains(videoPath)) {
      AppLogger.i('Thumbnail request already pending for: $videoPath');
      // Wait a bit and check cache again
      await Future.delayed(const Duration(milliseconds: 200));
      final retryPath = _thumbnailPathCache.get(videoPath);
      if (retryPath != null) {
        final exists = await File(retryPath).exists();
        if (exists) return retryPath;
      }
      return null;
    }

    try {
      _pendingRequests.add(videoPath);

      // Check disk cache
      final thumbnailPath = await _getThumbnailPath(videoPath);
      if (await File(thumbnailPath).exists()) {
        _thumbnailPathCache.put(videoPath, thumbnailPath);
        _pendingRequests.remove(videoPath);
        return thumbnailPath;
      }

      AppLogger.i('Generating thumbnail for: $videoPath');

      // Generate thumbnail using video_thumbnail package (on main thread but non-blocking)
      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: seekMs,
        quality: 60, // Reduced from 75 for better performance
      );

      if (thumbnailData != null && thumbnailData.isNotEmpty) {
        // Save to disk
        final file = File(thumbnailPath);
        await file.writeAsBytes(thumbnailData);

        _thumbnailPathCache.put(videoPath, thumbnailPath);
        _pendingRequests.remove(videoPath);
        return thumbnailPath;
      }

      _pendingRequests.remove(videoPath);
      return null;
    } catch (e, stackTrace) {
      AppLogger.e(
          'Error generating thumbnail for $videoPath: $e', e, stackTrace);
      _pendingRequests.remove(videoPath);
      return null;
    }
  }

  /// Get thumbnail file path (without generating)
  Future<String> _getThumbnailPath(String videoPath) async {
    final thumbnailDir = await _getThumbnailDirectory();
    return '$thumbnailDir/${videoPath.hashCode}.jpg';
  }

  /// Get the thumbnail directory
  Future<String> _getThumbnailDirectory() async {
    final directory = await getTemporaryDirectory();
    final thumbnailDir = Directory('${directory.path}/thumbnails');
    if (!await thumbnailDir.exists()) {
      await thumbnailDir.create(recursive: true);
    }
    return thumbnailDir.path;
  }

  /// Get cached thumbnail path if available
  String? getCachedThumbnail(String videoPath) {
    return _thumbnailPathCache.get(videoPath);
  }

  /// Check if thumbnail is cached
  bool isThumbnailCached(String videoPath) {
    return _thumbnailPathCache.containsKey(videoPath);
  }

  /// Clear all cached thumbnails
  Future<void> clearCache() async {
    _thumbnailPathCache.clear();
    _pendingRequests.clear();

    try {
      final thumbnailDir = await _getThumbnailDirectory();
      final dir = Directory(thumbnailDir);
      if (await dir.exists()) {
        await for (final entity in dir.list()) {
          if (entity is File) {
            await entity.delete();
          }
        }
      }
      AppLogger.i('Thumbnail cache cleared');
    } catch (e) {
      AppLogger.e('Error clearing thumbnail cache: $e');
    }
  }
}
