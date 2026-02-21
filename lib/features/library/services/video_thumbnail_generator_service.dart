import 'dart:io';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;

/// Simple thumbnail service - generates and caches thumbnails on disk
class VideoThumbnailService {
  static final VideoThumbnailService _instance =
      VideoThumbnailService._internal();
  factory VideoThumbnailService() => _instance;
  VideoThumbnailService._internal();

  /// Generate thumbnail for a video file
  Future<String?> generateThumbnail(String videoPath) async {
    try {
      final thumbnailDir = await _getThumbnailDirectory();

      // Generate thumbnail
      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: 1000,
        quality: 60,
      );

      if (thumbnailData == null || thumbnailData.isEmpty) {
        return null;
      }

      // Save to file
      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final thumbnailPath = path.join(thumbnailDir, fileName);
      final file = File(thumbnailPath);
      await file.writeAsBytes(thumbnailData);

      return thumbnailPath;
    } catch (e) {
      return null;
    }
  }

  /// Get thumbnail directory
  Future<String> _getThumbnailDirectory() async {
    final directory = await getTemporaryDirectory();
    final thumbnailDir = Directory('${directory.path}/thumbnails');
    if (!await thumbnailDir.exists()) {
      await thumbnailDir.create(recursive: true);
    }
    return thumbnailDir.path;
  }

  /// Get existing thumbnail path
  Future<String?> getThumbnail(String videoPath) async {
    try {
      final thumbnailDir = await _getThumbnailDirectory();
      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final thumbnailPath = path.join(thumbnailDir, fileName);
      final file = File(thumbnailPath);

      if (await file.exists()) {
        return thumbnailPath;
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /// Clear all thumbnails
  Future<void> clearCache() async {
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
      // Ignore
    }
  }
}
