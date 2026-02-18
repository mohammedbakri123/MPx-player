import 'package:path/path.dart' as path;
import 'package:photo_manager/photo_manager.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

/// Helper class for scanning videos using Android MediaStore
class MediaStoreScanner {
  static final List<String> _videoExtensions = [
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
  ];

  /// Minimum file size in bytes (100KB)
  static const int _minFileSize = 100 * 1024;

  /// Scan videos using Android MediaStore
  static Future<List<VideoFile>> scan(
    Function(double progress, String status)? onProgress,
  ) async {
    final videos = <VideoFile>[];

    try {
      // Request permission if needed
      final PermissionState permissionState =
          await PhotoManager.requestPermissionExtend();
      if (!permissionState.isAuth) {
        AppLogger.w('MediaStore permission denied');
        return videos;
      }

      AppLogger.i('Fetching videos from MediaStore...');

      // Fetch all video assets from MediaStore
      final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
        type: RequestType.video,
        filterOption: FilterOptionGroup(
          videoOption: const FilterOption(
            durationConstraint: DurationConstraint(
              min: Duration.zero,
            ),
          ),
        ),
      );

      AppLogger.i('Found ${albums.length} video albums in MediaStore');

      if (albums.isEmpty) {
        return videos;
      }

      int totalVideos = 0;
      for (final album in albums) {
        totalVideos += await album.assetCountAsync;
      }

      AppLogger.i('Total videos in MediaStore: $totalVideos');

      if (totalVideos == 0) {
        return videos;
      }

      int processedCount = 0;

      // Process each album
      for (final album in albums) {
        try {
          final List<AssetEntity> assets = await album.getAssetListPaged(
            page: 0,
            size: await album.assetCountAsync,
          );

          for (final asset in assets) {
            try {
              final String? filePath =
                  await asset.originFile.then((file) => file?.path);

              if (filePath == null || filePath.isEmpty) {
                continue;
              }

              final ext = path.extension(filePath).toLowerCase();
              if (!_videoExtensions.contains(ext)) {
                continue;
              }

              final folderPath = path.dirname(filePath);
              final folderName = path.basename(folderPath);

              final fileSize =
                  (await asset.originFile.then((file) => file?.lengthSync())) ??
                      0;

              if (fileSize < _minFileSize) {
                continue;
              }

              final video = VideoFile(
                id: asset.id,
                path: filePath,
                title: asset.title ?? path.basenameWithoutExtension(filePath),
                folderPath: folderPath,
                folderName: folderName,
                size: fileSize,
                duration: asset.duration * 1000,
                dateAdded: asset.createDateTime,
              );

              videos.add(video);
              processedCount++;

              if (processedCount % 10 == 0 && totalVideos > 0) {
                final progress = 0.05 + (0.85 * (processedCount / totalVideos));
                onProgress?.call(
                  progress,
                  'Loading videos from MediaStore ($processedCount/$totalVideos)...',
                );
              }
            } catch (e) {
              AppLogger.e('Error processing MediaStore asset: $e');
              continue;
            }
          }
        } catch (e) {
          AppLogger.e('Error processing album ${album.name}: $e');
          continue;
        }
      }

      AppLogger.i(
          'MediaStore scan complete. Found ${videos.length} valid videos');
    } catch (e, stackTrace) {
      AppLogger.e('Error scanning MediaStore: $e', e, stackTrace);
    }

    return videos;
  }
}
