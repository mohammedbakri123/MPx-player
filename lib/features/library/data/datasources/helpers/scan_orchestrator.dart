import 'package:flutter/foundation.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../../../../core/services/performance_monitor.dart';
import '../../../../library/services/persistent_cache_service.dart';
import '../../../domain/entities/video_folder.dart';
import 'media_store_scanner.dart';
import 'video_grouping_helper.dart';

/// ULTRA-FAST scanner - MX Player style
/// 
/// Uses ONLY Android MediaStore for instant scanning (1-2 seconds)
/// No file system scanning - Android already indexed everything!
class ScanOrchestrator {
  static Future<List<VideoFolder>> scan({
    required bool forceRefresh,
    required Function(double progress, String status)? onProgress,
  }) async {
    // Start performance tracking
    performanceMonitor.startScan();
    
    onProgress?.call(0.0, 'Initializing...');
    final stopwatch = Stopwatch()..start();

    // ALWAYS use MediaStore on Android - it's instant!
    if (defaultTargetPlatform == TargetPlatform.android) {
      try {
        final videos = await MediaStoreScanner.scan(
          onProgress: onProgress,
        );

        if (videos.isNotEmpty) {
          stopwatch.stop();
          AppLogger.i(
            '⚡ Scan complete in ${stopwatch.elapsedMilliseconds}ms! '
            'Found ${videos.length} videos',
          );

          final folders = VideoGroupingHelper.groupByFolder(videos);
          
          // Cache the results
          await PersistentCacheService.saveToCache(folders);
          
          // End performance tracking
          performanceMonitor.endScan(videos.length);
          
          return folders;
        }
      } catch (e, stackTrace) {
        AppLogger.e('MediaStore scan failed: $e', e, stackTrace);
        // Fall through to empty result
      }
    }

    // Non-Android or MediaStore failed
    AppLogger.w('No videos found or not on Android');
    performanceMonitor.endScan(0);
    return [];
  }
}
