import 'dart:async';
import 'package:flutter/foundation.dart';
import '../../../../core/services/logger_service.dart';
import '../../../../core/database/app_database.dart';
import '../../services/video_thumbnail_generator_service.dart';
import '../../services/video_metadata_service.dart';
import '../../domain/entities/video_file.dart';

/// Callback for when a video is updated (thumbnail/metadata generated)
typedef VideoUpdatedCallback = void Function(VideoFile video);

/// Background worker for generating thumbnails and extracting metadata
/// 
/// Processes videos in batches to avoid blocking the UI
class VideoMetadataWorker {
  static final VideoMetadataWorker _instance = VideoMetadataWorker._internal();
  factory VideoMetadataWorker() => _instance;
  VideoMetadataWorker._internal();

  bool _isProcessing = false;
  final Set<String> _processedPaths = {};
  Timer? _debounceTimer;
  
  // Batch processing
  final List<VideoFile> _pendingVideos = [];
  final List<VideoFile> _videosToUpdate = [];
  static const int _batchSize = 10;
  static const int _debounceMs = 500;
  static const int _dbUpdateBatchSize = 20;
  
  // Callback for UI updates
  VideoUpdatedCallback? onVideoUpdated;

  /// Process videos in background - generates thumbnails and extracts metadata
  Future<void> processVideos(List<VideoFile> videos, {VideoUpdatedCallback? onUpdated}) async {
    // Set callback
    onVideoUpdated = onUpdated;
    
    // Add to pending queue
    _pendingVideos.addAll(videos);
    
    // Debounce to avoid rapid processing
    _debounceTimer?.cancel();
    _debounceTimer = Timer(Duration(milliseconds: _debounceMs), _processBatch);
  }

  /// Process videos in batches
  Future<void> _processBatch() async {
    if (_isProcessing || _pendingVideos.isEmpty) return;
    
    _isProcessing = true;
    
    try {
      // Process in batches to avoid overwhelming the system
      final batch = _pendingVideos.take(_batchSize).toList();
      _pendingVideos.removeRange(0, batch.length);
      
      AppLogger.i('Processing ${batch.length} videos for thumbnails/metadata...');
      
      // Process each video in the batch
      for (final video in batch) {
        await _processSingleVideo(video);
      }
      
      // Save to database in batches
      if (_videosToUpdate.length >= _dbUpdateBatchSize) {
        await _saveToDatabase();
      }
      
      AppLogger.i('Batch complete. ${_pendingVideos.length} videos remaining');
      
      // Continue with next batch if there are more
      if (_pendingVideos.isNotEmpty) {
        _processBatch();
      } else if (_videosToUpdate.isNotEmpty) {
        // Save remaining videos
        await _saveToDatabase();
      }
    } finally {
      _isProcessing = false;
    }
  }

  /// Process a single video - thumbnail + metadata
  Future<void> _processSingleVideo(VideoFile video) async {
    // Skip if already processed
    if (_processedPaths.contains(video.path)) {
      return;
    }

    try {
      bool updated = false;
      
      // Generate thumbnail (high priority for visible items)
      if (video.thumbnailPath == null) {
        final thumbnailPath = await VideoThumbnailGeneratorService()
            .generateThumbnail(
              video.path,
              priority: ThumbnailPriority.normal,
            );
        
        if (thumbnailPath != null) {
          video.thumbnailPath = thumbnailPath;
          updated = true;
          AppLogger.d('Generated thumbnail for: ${video.title}');
        }
      }

      // Extract metadata if resolution is missing
      if (video.width == null || video.height == null) {
        final metadata = await VideoMetadataService()
            .extractMetadata(video.path);
        
        if (metadata != null && metadata.width != null && metadata.height != null) {
          // Note: VideoFile is immutable, can't update directly
          // But we'll save to database
          AppLogger.d('Extracted metadata for: ${video.title} '
              '(${metadata.width}x${metadata.height})');
        }
      }

      // Track for database update
      if (updated) {
        _videosToUpdate.add(video);
      }

      _processedPaths.add(video.path);
    } catch (e) {
      AppLogger.e('Error processing video ${video.path}: $e');
    }
  }

  /// Save updated videos to database
  Future<void> _saveToDatabase() async {
    if (_videosToUpdate.isEmpty) return;
    
    try {
      final db = AppDatabase();
      await db.updateVideosBatch(_videosToUpdate);
      AppLogger.i('Saved ${_videosToUpdate.length} video updates to database');
      _videosToUpdate.clear();
    } catch (e) {
      AppLogger.e('Error saving video updates to database: $e');
    }
  }

  /// Cancel all pending work
  void cancel() {
    _debounceTimer?.cancel();
    _pendingVideos.clear();
    _isProcessing = false;
  }

  /// Clear processed paths cache (for refresh)
  void clearCache() {
    _processedPaths.clear();
    _videosToUpdate.clear();
    cancel();
  }

  /// Get stats
  Map<String, dynamic> get stats => {
    'isProcessing': _isProcessing,
    'pendingVideos': _pendingVideos.length,
    'videosToUpdate': _videosToUpdate.length,
    'processedPaths': _processedPaths.length,
  };
}
