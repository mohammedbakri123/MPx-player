import 'dart:io';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path/path.dart' as path;
import 'thumbnail_worker_pool.dart';

class VideoThumbnailService {
  static final VideoThumbnailService _instance =
      VideoThumbnailService._internal();
  factory VideoThumbnailService() => _instance;
  VideoThumbnailService._internal();

  Future<String?> generateThumbnail(String videoPath) async {
    try {
      final thumbnailDir = await getPersistentThumbnailDirectory();

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

      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final thumbnailPath = path.join(thumbnailDir, fileName);
      final file = File(thumbnailPath);
      await file.writeAsBytes(thumbnailData);

      return thumbnailPath;
    } catch (e) {
      return null;
    }
  }

  Future<String?> getThumbnail(String videoPath) async {
    try {
      final thumbnailDir = await getPersistentThumbnailDirectory();
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

  Future<void> clearCache() async {
    try {
      final thumbnailDir = await getPersistentThumbnailDirectory();
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
