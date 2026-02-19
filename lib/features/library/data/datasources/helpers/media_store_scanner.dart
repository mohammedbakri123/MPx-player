import 'dart:async';
import 'package:photo_manager/photo_manager.dart';
import 'package:path/path.dart' as path;
import '../../../../../core/services/logger_service.dart';
import '../../../services/video_thumbnail_generator_service.dart';
import '../../../domain/entities/video_file.dart';

/// ULTRA-FAST MediaStore scanner with INSTANT thumbnails
///
/// Generates thumbnails WHILE scanning MediaStore for maximum speed
class MediaStoreScanner {
  /// Scan videos using Android MediaStore - with thumbnails!
  static Future<List<VideoFile>> scan({
    Function(double progress, String status)? onProgress,
    bool generateThumbnails = true,
  }) async {
    final videos = <VideoFile>[];
    final stopwatch = Stopwatch()..start();

    try {
      onProgress?.call(0.0, 'Requesting permission...');

      // Request permission
      final PermissionState permissionState =
          await PhotoManager.requestPermissionExtend();

      if (!permissionState.isAuth) {
        AppLogger.w('MediaStore permission denied');
        return videos;
      }

      onProgress?.call(0.1, 'Querying MediaStore...');

      // Get all video paths from MediaStore
      final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
        type: RequestType.video,
      );

      AppLogger.i('Found ${albums.length} albums in MediaStore');

      if (albums.isEmpty) {
        return videos;
      }

      // Count total videos
      int totalVideos = 0;
      for (final album in albums) {
        totalVideos += await album.assetCountAsync;
      }

      AppLogger.i('Total videos in MediaStore: $totalVideos');
      onProgress?.call(0.2, 'Found $totalVideos videos, loading...');

      if (totalVideos == 0) {
        return videos;
      }

      // Process albums with thumbnail generation
      int processedCount = 0;
      final videoFutures = <Future<List<VideoFile>>>[];

      for (final album in albums) {
        videoFutures.add(_processAlbum(album, generateThumbnails));
      }

      // Wait for all albums to process concurrently
      final results = await Future.wait(videoFutures);

      // Combine all results
      for (final albumVideos in results) {
        videos.addAll(albumVideos);
        processedCount += albumVideos.length;

        final progress = 0.3 + (0.7 * (processedCount / totalVideos));
        onProgress?.call(
          progress,
          'Loaded $processedCount/$totalVideos videos',
        );
      }

      stopwatch.stop();
      AppLogger.i(
        '⚡ MediaStore scan complete in ${stopwatch.elapsedMilliseconds}ms! '
        'Found ${videos.length} videos',
      );

      return videos;
    } catch (e, stackTrace) {
      AppLogger.e('Error scanning MediaStore: $e', e, stackTrace);
      return [];
    }
  }

  /// Process a single album - with thumbnail generation
  static Future<List<VideoFile>> _processAlbum(
    AssetPathEntity album,
    bool generateThumbnails,
  ) async {
    final videos = <VideoFile>[];

    try {
      final String albumName = album.name;
      final int assetCount = await album.assetCountAsync;

      if (assetCount == 0) return videos;

      // Get all assets
      final List<AssetEntity> assets = await album.getAssetListPaged(
        page: 0,
        size: assetCount,
      );

      for (final asset in assets) {
        try {
          // Get file path
          final String filePath =
              await asset.originFile.then((file) => file!.path);

          // Quick extension check
          final String ext = path.extension(filePath).toLowerCase();
          if (!_isValidVideoExtension(ext)) continue;

          // Get file size
          final int fileSize =
              await asset.originFile.then((file) => file!.lengthSync());

          // Skip tiny files (< 100KB)
          if (fileSize < 100 * 1024) continue;

          // Get resolution from MediaStore
          final int width = asset.width;
          final int height = asset.height;

          // Generate thumbnail immediately (synchronous for first 10)
          String? thumbnailPath;
          if (generateThumbnails && videos.length < 10) {
            // Generate synchronously for first 10 videos (faster initial load)
            thumbnailPath = await VideoThumbnailGeneratorService()
                .generateThumbnail(filePath, priority: ThumbnailPriority.high);
          } else if (generateThumbnails) {
            // Generate asynchronously for rest
            VideoThumbnailGeneratorService()
                .generateThumbnail(filePath, priority: ThumbnailPriority.low);
          }

          videos.add(VideoFile(
            id: asset.id,
            path: filePath,
            title: asset.title ?? path.basenameWithoutExtension(filePath),
            folderPath: path.dirname(filePath),
            folderName: albumName,
            size: fileSize,
            duration: asset.duration * 1000,
            dateAdded: asset.createDateTime,
            width: width,
            height: height,
            thumbnailPath: thumbnailPath,
          ));
        } catch (e) {
          continue;
        }
      }
    } catch (e) {
      AppLogger.e('Error processing album: $e');
    }

    return videos;
  }

  static bool _isValidVideoExtension(String ext) {
    return const {
      '.mp4',
      '.mkv',
      '.avi',
      '.mov',
      '.wmv',
      '.flv',
      '.webm',
      '.m4v',
      '.3gp',
      '.ts',
      '.mts',
      '.m2ts'
    }.contains(ext);
  }
}
