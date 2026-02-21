import 'package:flutter/foundation.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../../../../core/services/performance_monitor.dart';
import '../../../../library/services/persistent_cache_service.dart';
import '../../../../library/services/thumbnail_pre_generation_service.dart';
import '../../../domain/entities/video_file.dart';
import '../../../domain/entities/video_folder.dart';
import 'media_store_scanner.dart';
import 'video_grouping_helper.dart';

class ScanOrchestrator {
  static Future<List<VideoFolder>> scan({
    required bool forceRefresh,
    required Function(double progress, String status)? onProgress,
    bool generateThumbnails = true,
  }) async {
    performanceMonitor.startScan();

    onProgress?.call(0.0, 'Initializing...');
    final stopwatch = Stopwatch()..start();

    if (defaultTargetPlatform == TargetPlatform.android) {
      try {
        final videos = await MediaStoreScanner.scan(
          onProgress: (progress, status) {
            onProgress?.call(progress * 0.4, status);
          },
        );

        if (videos.isNotEmpty) {
          stopwatch.stop();
          AppLogger.i(
            '⚡ Scan complete in ${stopwatch.elapsedMilliseconds}ms! '
            'Found ${videos.length} videos',
          );

          final folders = VideoGroupingHelper.groupByFolder(videos);

          if (generateThumbnails) {
            onProgress?.call(0.4, 'Generating thumbnails...');
            await _generateThumbnails(videos, onProgress);
          }

          await PersistentCacheService.saveToCache(folders);
          performanceMonitor.endScan(videos.length);

          return folders;
        }
      } catch (e, stackTrace) {
        AppLogger.e('MediaStore scan failed: $e', e, stackTrace);
      }
    }

    AppLogger.w('No videos found or not on Android');
    performanceMonitor.endScan(0);
    return [];
  }

  static Future<void> _generateThumbnails(
    List<VideoFile> videos,
    Function(double progress, String status)? onProgress,
  ) async {
    final thumbnailService = ThumbnailPreGenerationService();

    await thumbnailService.generateThumbnails(
      videos,
      onProgress: (progress, completed, total, status) {
        final adjustedProgress = 0.4 + (progress * 0.6);
        onProgress?.call(adjustedProgress, status);
      },
    );
  }
}
