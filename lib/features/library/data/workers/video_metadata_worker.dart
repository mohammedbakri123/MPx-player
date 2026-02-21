import 'dart:async';
import '../../../../core/services/logger_service.dart';
import '../../services/video_metadata_service.dart';
import '../../domain/entities/video_file.dart';

/// Background worker for extracting video metadata
class VideoMetadataWorker {
  static final VideoMetadataWorker _instance = VideoMetadataWorker._internal();
  factory VideoMetadataWorker() => _instance;
  VideoMetadataWorker._internal();

  bool _isProcessing = false;
  final Set<String> _processedPaths = {};
  Timer? _debounceTimer;

  // Batch processing
  final List<VideoFile> _pendingVideos = [];
  static const int _batchSize = 10;
  static const int _debounceMs = 500;

  /// Process videos in background - extracts metadata
  Future<void> processVideos(List<VideoFile> videos) async {
    _pendingVideos.addAll(videos);

    _debounceTimer?.cancel();
    _debounceTimer =
        Timer(const Duration(milliseconds: _debounceMs), _processBatch);
  }

  /// Process videos in batches
  Future<void> _processBatch() async {
    if (_isProcessing || _pendingVideos.isEmpty) return;

    _isProcessing = true;

    try {
      final batch = _pendingVideos.take(_batchSize).toList();
      _pendingVideos.removeRange(0, batch.length);

      AppLogger.i('Processing ${batch.length} videos for metadata...');

      for (final video in batch) {
        await _processSingleVideo(video);
      }

      AppLogger.i('Batch complete. ${_pendingVideos.length} videos remaining');

      if (_pendingVideos.isNotEmpty) {
        _processBatch();
      }
    } finally {
      _isProcessing = false;
    }
  }

  /// Process a single video - extract metadata
  Future<void> _processSingleVideo(VideoFile video) async {
    if (_processedPaths.contains(video.path)) return;

    try {
      if (video.width == null || video.height == null) {
        final metadata =
            await VideoMetadataService().extractMetadata(video.path);

        if (metadata != null &&
            metadata.width != null &&
            metadata.height != null) {
          AppLogger.d(
              'Extracted metadata for: ${video.title} (${metadata.width}x${metadata.height})');
        }
      }

      _processedPaths.add(video.path);
    } catch (e) {
      AppLogger.e('Error processing video ${video.path}: $e');
    }
  }

  /// Cancel all pending work
  void cancel() {
    _debounceTimer?.cancel();
    _pendingVideos.clear();
    _isProcessing = false;
  }

  /// Clear processed paths cache
  void clearCache() {
    _processedPaths.clear();
    cancel();
  }
}
