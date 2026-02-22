import 'package:flutter/foundation.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../../../../core/services/performance_monitor.dart';
import '../../../../library/services/persistent_cache_service.dart';
import '../../../../library/services/scan_metadata_service.dart';
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
          await ScanMetadataService.setLastScanTimestamp(DateTime.now());
          await ScanMetadataService.setLastFullScanTimestamp(DateTime.now());
          await ScanMetadataService.updateScanStats(
            videoCount: videos.length,
            folderCount: folders.length,
            durationMs: stopwatch.elapsedMilliseconds,
          );
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

  static Future<IncrementalScanResult> scanIncremental({
    required Function(double progress, String status)? onProgress,
    bool generateThumbnails = true,
  }) async {
    performanceMonitor.startScan();

    onProgress?.call(0.0, 'Checking for new videos...');
    final stopwatch = Stopwatch()..start();

    final lastScan = await ScanMetadataService.getLastScanTimestamp();
    if (lastScan == null) {
      AppLogger.w('No previous scan found, falling back to full scan');
      final folders = await scan(
        forceRefresh: true,
        onProgress: onProgress,
        generateThumbnails: generateThumbnails,
      );
      return IncrementalScanResult(
        folders: folders,
        isFullScan: true,
        newVideosCount: folders.fold(0, (sum, f) => sum + f.videos.length),
      );
    }

    if (defaultTargetPlatform == TargetPlatform.android) {
      try {
        final videos = await MediaStoreScanner.scan(
          onProgress: (progress, status) {
            onProgress?.call(progress * 0.4, status);
          },
          sinceTimestamp: lastScan,
        );

        stopwatch.stop();
        AppLogger.i(
          '⚡ Incremental scan complete in ${stopwatch.elapsedMilliseconds}ms! '
          'Found ${videos.length} new videos',
        );

        if (videos.isEmpty) {
          return IncrementalScanResult(
            folders: [],
            isFullScan: false,
            newVideosCount: 0,
          );
        }

        final cachedFolders = await PersistentCacheService.loadFromCache();
        final mergedResult = await _mergeIncrementalResults(
          cachedFolders: cachedFolders ?? [],
          newVideos: videos,
        );

        if (generateThumbnails) {
          onProgress?.call(0.4, 'Generating thumbnails...');
          await _generateThumbnails(videos, onProgress);
        }

        await _saveIncrementalResults(
            mergedResult.addedVideos, mergedResult.updatedFolders);
        await ScanMetadataService.setLastScanTimestamp(DateTime.now());
        await ScanMetadataService.updateScanStats(
          videoCount: mergedResult.totalVideos,
          folderCount: mergedResult.updatedFolders.length,
          durationMs: stopwatch.elapsedMilliseconds,
        );
        performanceMonitor.endScan(videos.length);

        return IncrementalScanResult(
          folders: mergedResult.updatedFolders,
          isFullScan: false,
          newVideosCount: videos.length,
          addedVideos: mergedResult.addedVideos,
        );
      } catch (e, stackTrace) {
        AppLogger.e('Incremental scan failed: $e', e, stackTrace);
      }
    }

    performanceMonitor.endScan(0);
    return IncrementalScanResult(
        folders: [], isFullScan: false, newVideosCount: 0);
  }

  static Future<MergeResult> _mergeIncrementalResults({
    required List<VideoFolder> cachedFolders,
    required List<VideoFile> newVideos,
  }) async {
    final folderMap = <String, List<VideoFile>>{};

    for (final folder in cachedFolders) {
      folderMap[folder.path] = List.from(folder.videos);
    }

    final seenPaths = <String>{};
    for (final folder in cachedFolders) {
      for (final video in folder.videos) {
        seenPaths.add(video.path);
      }
    }

    final addedVideos = <VideoFile>[];
    for (final video in newVideos) {
      if (!seenPaths.contains(video.path)) {
        addedVideos.add(video);
        seenPaths.add(video.path);

        if (!folderMap.containsKey(video.folderPath)) {
          folderMap[video.folderPath] = [];
        }
        folderMap[video.folderPath]!.add(video);
      }
    }

    final updatedFolders = folderMap.entries.map((entry) {
      return VideoFolder(
        path: entry.key,
        name: entry.value.firstOrNull?.folderName ?? entry.key.split('/').last,
        videos: entry.value,
      );
    }).toList()
      ..sort((a, b) => b.videos.length.compareTo(a.videos.length));

    final totalVideos =
        updatedFolders.fold(0, (sum, f) => sum + f.videos.length);

    return MergeResult(
      updatedFolders: updatedFolders,
      addedVideos: addedVideos,
      totalVideos: totalVideos,
    );
  }

  static Future<void> _saveIncrementalResults(
    List<VideoFile> addedVideos,
    List<VideoFolder> updatedFolders,
  ) async {
    if (addedVideos.isEmpty) return;

    await PersistentCacheService.saveIncrementalVideos(
        addedVideos, updatedFolders);
    AppLogger.i('Saved ${addedVideos.length} new videos to database');
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

class IncrementalScanResult {
  final List<VideoFolder> folders;
  final bool isFullScan;
  final int newVideosCount;
  final List<VideoFile>? addedVideos;

  IncrementalScanResult({
    required this.folders,
    required this.isFullScan,
    required this.newVideosCount,
    this.addedVideos,
  });
}

class MergeResult {
  final List<VideoFolder> updatedFolders;
  final List<VideoFile> addedVideos;
  final int totalVideos;

  MergeResult({
    required this.updatedFolders,
    required this.addedVideos,
    required this.totalVideos,
  });
}
