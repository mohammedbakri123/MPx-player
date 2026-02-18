import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:pool/pool.dart';
import '../../../../../../core/services/logger_service.dart';
import '../../../../../../core/services/persistent_cache_service.dart';
import '../../../domain/entities/video_file.dart';
import '../../../domain/entities/video_folder.dart';
import 'directory_discovery_helper.dart';
import 'media_store_scanner.dart';
import 'video_grouping_helper.dart';
import 'file_scanner_helper.dart';

/// Orchestrates the video scanning process
class ScanOrchestrator {
  static Future<List<VideoFolder>> scan({
    required bool forceRefresh,
    required Function(double progress, String status)? onProgress,
  }) async {
    onProgress?.call(0.0, 'Initializing scan...');

    final videos = <VideoFile>[];
    final stopwatch = Stopwatch()..start();

    // Try MediaStore first on Android
    if (defaultTargetPlatform == TargetPlatform.android && !forceRefresh) {
      onProgress?.call(0.05, 'Scanning with MediaStore...');
      final mediaStoreVideos = await MediaStoreScanner.scan(onProgress);

      if (mediaStoreVideos.isNotEmpty) {
        videos.addAll(mediaStoreVideos);
        stopwatch.stop();
        AppLogger.i(
            'MediaStore scan complete in ${stopwatch.elapsedMilliseconds}ms');
        return VideoGroupingHelper.groupByFolder(videos);
      }
    }

    // Fall back to file system scanning
    final directories = await DirectoryDiscoveryHelper.getDirectoriesToScan();
    onProgress?.call(0.1, 'Found ${directories.length} directories');

    if (directories.isEmpty) return [];

    final previousMetadata = await PersistentCacheService.loadFileMetadata();
    final currentMetadata = <String, DateTime>{};

    await _scanDirectoriesConcurrently(
      directories,
      videos,
      forceRefresh: forceRefresh,
      previousMetadata: previousMetadata,
      currentMetadata: currentMetadata,
      onProgress: onProgress,
    );

    stopwatch.stop();
    AppLogger.i(
        'Scan complete in ${stopwatch.elapsedMilliseconds}ms. Found ${videos.length} videos');

    if (videos.isNotEmpty) {
      final folders = VideoGroupingHelper.groupByFolder(videos);
      await PersistentCacheService.saveToCache(folders);
      await PersistentCacheService.saveFileMetadata(currentMetadata);
      return folders;
    }

    return [];
  }

  static Future<void> _scanDirectoriesConcurrently(
    List<Directory> directories,
    List<VideoFile> videos, {
    required bool forceRefresh,
    required Map<String, DateTime>? previousMetadata,
    required Map<String, DateTime> currentMetadata,
    required Function(double progress, String status)? onProgress,
  }) async {
    final pool = Pool(3);
    final futures = <Future<void>>[];
    final processedPaths = <String>{}; // Shared set for deduplication
    var scanIndex = 0;

    for (var i = 0; i < directories.length; i++) {
      final dir = directories[i];
      if (await dir.exists()) {
        final currentIndex = scanIndex++;

        futures.add(pool.withResource(() async {
          await FileScannerHelper.scanDirectory(
            dir,
            videos,
            currentFileMetadata: !forceRefresh ? currentMetadata : null,
            previousFileMetadata: !forceRefresh ? previousMetadata : null,
            processedPaths: processedPaths,
            onProgress: onProgress,
            currentDirIndex: currentIndex,
            totalDirs: directories.length,
          );
        }));
      }
    }

    await Future.wait(futures);
    await pool.close();
    AppLogger.i(
        'Scanned ${videos.length} unique videos from ${directories.length} directories');
    onProgress?.call(0.95, 'Processing results...');
  }
}
