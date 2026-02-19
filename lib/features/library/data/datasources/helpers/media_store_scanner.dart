import 'package:path/path.dart' as path;
import 'package:photo_manager/photo_manager.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

/// ULTRA-FAST MediaStore scanner - MX Player style
/// 
/// Uses Android MediaStore exclusively for instant scanning (1-2 seconds)
/// No file system scanning needed - Android already indexed everything!
class MediaStoreScanner {
  /// Scan videos using Android MediaStore - FAST!
  static Future<List<VideoFile>> scan({
    Function(double progress, String status)? onProgress,
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

      // Get all video paths from MediaStore - THIS IS INSTANT!
      // MediaStore already has everything indexed by Android
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

      // Process albums in parallel for maximum speed
      int processedCount = 0;
      final videoFutures = <Future<List<VideoFile>>>[];

      for (final album in albums) {
        videoFutures.add(_processAlbum(album));
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

  /// Process a single album - optimized for speed
  static Future<List<VideoFile>> _processAlbum(AssetPathEntity album) async {
    final videos = <VideoFile>[];
    
    try {
      final String albumName = album.name ?? 'Unknown';
      final int assetCount = await album.assetCountAsync;
      
      if (assetCount == 0) return videos;

      // Get all assets - use getAssetListPaged for better performance
      final List<AssetEntity> assets = await album.getAssetListPaged(
        page: 0,
        size: assetCount,
      );

      for (final asset in assets) {
        try {
          // Get file path - this is fast with MediaStore
          final String filePath = await asset.originFile.then((file) => file!.path);

          // Quick extension check
          final String ext = path.extension(filePath).toLowerCase();
          if (!_isValidVideoExtension(ext)) continue;

          // Get file size quickly
          final int fileSize = await asset.originFile.then((file) => file!.lengthSync());
          
          // Skip tiny files (< 100KB for safety)
          if (fileSize < 100 * 1024) continue;

          // Get resolution from MediaStore (may be null for some videos)
          final int? width = asset.width;
          final int? height = asset.height;
          
          if (width != null && height != null) {
            AppLogger.d('MediaStore has resolution: ${width}x${height} for ${asset.title}');
          } else {
            AppLogger.d('MediaStore missing resolution for ${asset.title}, will extract later');
          }

          videos.add(VideoFile(
            id: asset.id,
            path: filePath,
            title: asset.title ?? path.basenameWithoutExtension(filePath),
            folderPath: path.dirname(filePath),
            folderName: albumName,
            size: fileSize,
            duration: asset.duration * 1000, // Convert seconds to milliseconds
            dateAdded: asset.createDateTime,
            width: width, // May be null
            height: height, // May be null
          ));
        } catch (e) {
          // Skip problematic assets
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
      '.mp4', '.mkv', '.avi', '.mov', '.wmv',
      '.flv', '.webm', '.m4v', '.3gp', '.ts',
      '.mts', '.m2ts'
    }.contains(ext);
  }
}
