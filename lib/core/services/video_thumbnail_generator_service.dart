import 'dart:io';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path_provider/path_provider.dart';

/// Service for generating video thumbnails using native platform APIs
/// Android: MediaMetadataRetriever
/// iOS: AVAssetImageGenerator
class VideoThumbnailGeneratorService {
  static final VideoThumbnailGeneratorService _instance =
      VideoThumbnailGeneratorService._internal();
  factory VideoThumbnailGeneratorService() => _instance;
  VideoThumbnailGeneratorService._internal();

  // Cache for thumbnail file paths
  final Map<String, String> _thumbnailPathCache = {};

  /// Generate a thumbnail for a video file
  /// Returns the file path to the generated thumbnail
  Future<String?> generateThumbnail(String videoPath,
      {int seekMs = 1000}) async {
    // Check cache first
    if (_thumbnailPathCache.containsKey(videoPath)) {
      final cachedPath = _thumbnailPathCache[videoPath]!;
      if (File(cachedPath).existsSync()) {
        return cachedPath;
      }
    }

    try {
      // Get thumbnail directory
      final thumbnailDir = await _getThumbnailDirectory();
      final thumbnailPath = '$thumbnailDir/${videoPath.hashCode}.jpg';

      // Check if already cached on disk
      if (File(thumbnailPath).existsSync()) {
        _thumbnailPathCache[videoPath] = thumbnailPath;
        return thumbnailPath;
      }

      // Generate thumbnail using video_thumbnail package
      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: seekMs,
        quality: 75,
      );

      if (thumbnailData != null && thumbnailData.isNotEmpty) {
        // Save to disk
        final file = File(thumbnailPath);
        await file.writeAsBytes(thumbnailData);

        _thumbnailPathCache[videoPath] = thumbnailPath;
        return thumbnailPath;
      }

      return null;
    } catch (e) {
      return null;
    }
  }

  /// Get the thumbnail directory
  Future<String> _getThumbnailDirectory() async {
    final directory = await getApplicationDocumentsDirectory();
    final thumbnailDir = Directory('${directory.path}/thumbnails');
    if (!thumbnailDir.existsSync()) {
      thumbnailDir.createSync(recursive: true);
    }
    return thumbnailDir.path;
  }

  /// Clear all cached thumbnails
  Future<void> clearCache() async {
    _thumbnailPathCache.clear();

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
    } catch (e) {
      // Ignore cleanup errors
    }
  }
}
