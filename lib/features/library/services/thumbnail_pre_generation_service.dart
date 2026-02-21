import 'dart:io';
import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';
import '../../library/domain/entities/video_file.dart';
import 'thumbnail_worker_pool.dart';

class ThumbnailPreGenerationService {
  static final ThumbnailPreGenerationService _instance =
      ThumbnailPreGenerationService._internal();
  factory ThumbnailPreGenerationService() => _instance;
  ThumbnailPreGenerationService._internal();

  final ThumbnailWorkerPool _workerPool = ThumbnailWorkerPool();
  final AppDatabase _database = AppDatabase();

  bool _isGenerating = false;
  int _completedCount = 0;
  int _totalCount = 0;

  bool get isGenerating => _isGenerating;
  double get progress => _totalCount > 0 ? _completedCount / _totalCount : 0.0;

  Future<void> generateThumbnails(
    List<VideoFile> videos, {
    Function(double progress, int completed, int total, String status)?
        onProgress,
    int batchSize = 10,
    int concurrencyLimit = 3,
  }) async {
    if (_isGenerating) {
      AppLogger.w('Thumbnail generation already in progress');
      return;
    }

    _isGenerating = true;
    _completedCount = 0;
    _totalCount = videos.length;

    final stopwatch = Stopwatch()..start();
    final updatedVideos = <VideoFile>[];

    onProgress?.call(0.0, 0, _totalCount, 'Starting thumbnail generation...');

    final videosToProcess = await _filterVideosNeedingThumbnails(videos);
    AppLogger.i(
        'Thumbnails to generate: ${videosToProcess.length}/${videos.length}');

    if (videosToProcess.isEmpty) {
      onProgress?.call(
          1.0, _totalCount, _totalCount, 'All thumbnails already cached');
      _isGenerating = false;
      return;
    }

    for (var i = 0; i < videosToProcess.length; i += batchSize) {
      final batch = videosToProcess.skip(i).take(batchSize).toList();
      final results = await _processBatch(batch, concurrencyLimit);

      for (final result in results) {
        _completedCount++;
        final progressValue = _completedCount / _totalCount;
        onProgress?.call(
          progressValue,
          _completedCount,
          _totalCount,
          'Generated $_completedCount/$_totalCount thumbnails',
        );

        if (result.thumbnailPath != null) {
          final video =
              videosToProcess.firstWhere((v) => v.path == result.videoPath);
          updatedVideos.add(video..thumbnailPath = result.thumbnailPath);
        }
      }
    }

    if (updatedVideos.isNotEmpty) {
      await _database.updateVideosBatch(updatedVideos);
      AppLogger.i(
          'Updated ${updatedVideos.length} video thumbnails in database');
    }

    stopwatch.stop();
    AppLogger.i(
      'Thumbnail generation complete: $_completedCount/${videos.length} in ${stopwatch.elapsedMilliseconds}ms',
    );

    _isGenerating = false;
    onProgress?.call(
        1.0, _totalCount, _totalCount, 'Thumbnail generation complete');
  }

  Future<List<VideoFile>> _filterVideosNeedingThumbnails(
      List<VideoFile> videos) async {
    final needThumbnails = <VideoFile>[];

    for (final video in videos) {
      if (video.thumbnailPath == null || video.thumbnailPath!.isEmpty) {
        needThumbnails.add(video);
        continue;
      }

      final file = File(video.thumbnailPath!);
      if (!await file.exists()) {
        needThumbnails.add(video);
      }
    }

    return needThumbnails;
  }

  Future<List<_ThumbnailResult>> _processBatch(
    List<VideoFile> batch,
    int concurrencyLimit,
  ) async {
    final results = <_ThumbnailResult>[];
    final queue = List<VideoFile>.from(batch);

    while (queue.isNotEmpty) {
      final currentBatch = queue.take(concurrencyLimit).toList();
      queue.removeRange(0, currentBatch.length);

      final futures = currentBatch.map((video) async {
        final thumbnailPath = await _workerPool.generateThumbnail(
          video.path,
          smartTimestamp: true,
          durationMs: video.duration > 0 ? video.duration : null,
        );
        return _ThumbnailResult(video.path, thumbnailPath);
      });

      final batchResults = await Future.wait(futures);
      results.addAll(batchResults);
    }

    return results;
  }

  Future<void> generateThumbnailForVideo(VideoFile video) async {
    final thumbnailPath = await _workerPool.generateThumbnail(
      video.path,
      smartTimestamp: true,
      durationMs: video.duration > 0 ? video.duration : null,
    );
    if (thumbnailPath != null) {
      video.thumbnailPath = thumbnailPath;
      await _database.updateVideoThumbnail(video.path, thumbnailPath);
    }
  }

  Future<void> cancelGeneration() async {
    _isGenerating = false;
    _completedCount = 0;
    _totalCount = 0;
  }

  Future<void> clearAllThumbnails() async {
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
      AppLogger.i('All thumbnails cleared');
    } catch (e) {
      AppLogger.e('Error clearing thumbnails: $e');
    }
  }
}

class _ThumbnailResult {
  final String videoPath;
  final String? thumbnailPath;
  _ThumbnailResult(this.videoPath, this.thumbnailPath);
}
