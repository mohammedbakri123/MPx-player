import 'dart:async';
import 'package:photo_manager/photo_manager.dart';
import 'package:path/path.dart' as path;
import '../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

class MediaStoreScanner {
  static Future<List<VideoFile>> scan({
    Function(double progress, String status)? onProgress,
    DateTime? sinceTimestamp,
  }) async {
    final videos = <VideoFile>[];
    final stopwatch = Stopwatch()..start();
    final isIncremental = sinceTimestamp != null;

    try {
      final PermissionState ps = await PhotoManager.requestPermissionExtend();
      if (!ps.isAuth) {
        AppLogger.w('MediaStore permission denied');
        return videos;
      }

      final filterOption = _buildFilterOption(sinceTimestamp: sinceTimestamp);

      final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
        type: RequestType.video,
        hasAll: true,
        filterOption: filterOption,
      );

      if (albums.isEmpty) {
        AppLogger.i('No video albums found');
        return videos;
      }

      final scanType = isIncremental ? 'Incremental' : 'Full';
      AppLogger.i(
          '$scanType scan: Found ${albums.length} albums${isIncremental ? ' (since $sinceTimestamp)' : ''}');

      for (var i = 0; i < albums.length; i++) {
        final album = albums[i];
        await _processAlbum(album, videos, onProgress,
            isIncremental: isIncremental);
      }

      stopwatch.stop();
      AppLogger.i(
          'MediaStore ${isIncremental ? 'incremental' : 'full'} scan complete: ${videos.length} videos in ${stopwatch.elapsedMilliseconds}ms');
      return videos;
    } catch (e, stack) {
      AppLogger.e('MediaStore scan error: $e', e, stack);
      return videos;
    }
  }

  static FilterOptionGroup _buildFilterOption({DateTime? sinceTimestamp}) {
    final option = FilterOption(
      sizeConstraint: SizeConstraint(
        minWidth: 0,
        minHeight: 0,
        maxWidth: 100000,
        maxHeight: 100000,
        ignoreSize: false,
      ),
    );

    FilterOptionGroup filterGroup;
    if (sinceTimestamp != null) {
      filterGroup = FilterOptionGroup(
        videoOption: option,
        createTimeCond: DateTimeCond(
          min: sinceTimestamp,
          max: DateTime.now(),
        ),
      );
    } else {
      filterGroup = FilterOptionGroup(videoOption: option);
    }

    return filterGroup;
  }

  /// Process a single album
  static Future<void> _processAlbum(
    AssetPathEntity album,
    List<VideoFile> videos,
    Function(double progress, String status)? onProgress, {
    bool isIncremental = false,
  }) async {
    try {
      final String albumName = album.name;
      final int assetCount = await album.assetCountAsync;

      if (assetCount == 0) return;

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
          ));
        } catch (e) {
          continue;
        }
      }
    } catch (e) {
      AppLogger.e('Error processing album: $e');
    }
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
