import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'package:path_provider/path_provider.dart';
import '../utils/lru_cache.dart';
import '../utils/cancellation_token.dart';
import 'logger_service.dart';

/// Priority levels for thumbnail generation
enum ThumbnailPriority { high, normal, low }

/// Request object for thumbnail generation
class _ThumbnailRequest {
  final String videoPath;
  final int seekMs;
  final ThumbnailPriority priority;
  final CancellationToken? cancellationToken;
  final Completer<String?> completer;

  _ThumbnailRequest({
    required this.videoPath,
    required this.seekMs,
    required this.priority,
    this.cancellationToken,
    required this.completer,
  });
}

/// Service for generating video thumbnails with priority queue and cancellation
class VideoThumbnailGeneratorService {
  static final VideoThumbnailGeneratorService _instance =
      VideoThumbnailGeneratorService._internal();
  factory VideoThumbnailGeneratorService() => _instance;
  VideoThumbnailGeneratorService._internal();

  // LRU Cache with 200 item limit
  final LRUCache<String, String> _thumbnailPathCache =
      LRUCache<String, String>(200);

  // Priority queue: high priority first, then normal, then low
  final Queue<_ThumbnailRequest> _requestQueue = Queue<_ThumbnailRequest>();
  final Set<String> _pendingPaths = {};
  bool _isProcessing = false;

  /// Maximum concurrent generations
  static const int _maxConcurrent = 2;
  int _currentConcurrent = 0;

  /// Generate a thumbnail with priority and optional cancellation
  Future<String?> generateThumbnail(
    String videoPath, {
    int seekMs = 1000,
    ThumbnailPriority priority = ThumbnailPriority.normal,
    CancellationToken? cancellationToken,
  }) async {
    // Check if cancelled before starting
    if (cancellationToken?.isCancelled ?? false) {
      return null;
    }

    // Check cache first
    final cachedPath = _thumbnailPathCache.get(videoPath);
    if (cachedPath != null) {
      final exists = await File(cachedPath).exists();
      if (exists) {
        return cachedPath;
      }
    }

    // Check disk cache
    final thumbnailPath = await _getThumbnailPath(videoPath);
    if (await File(thumbnailPath).exists()) {
      _thumbnailPathCache.put(videoPath, thumbnailPath);
      return thumbnailPath;
    }

    // Check if already in queue
    if (_pendingPaths.contains(videoPath)) {
      // Wait for existing request to complete
      await Future.delayed(const Duration(milliseconds: 100));
      if (cancellationToken?.isCancelled ?? false) return null;

      final cached = _thumbnailPathCache.get(videoPath);
      if (cached != null && await File(cached).exists()) {
        return cached;
      }
    }

    // Create request
    final completer = Completer<String?>();
    final request = _ThumbnailRequest(
      videoPath: videoPath,
      seekMs: seekMs,
      priority: priority,
      cancellationToken: cancellationToken,
      completer: completer,
    );

    // Add to queue based on priority
    _addToQueue(request);

    // Start processing if not already
    _processQueue();

    return completer.future;
  }

  void _addToQueue(_ThumbnailRequest request) {
    _pendingPaths.add(request.videoPath);

    // Insert based on priority (high -> front, low -> back)
    if (request.priority == ThumbnailPriority.high) {
      // Find first non-high priority item and insert before it
      final insertIndex = _requestQueue.toList().indexWhere(
            (r) => r.priority != ThumbnailPriority.high,
          );
      if (insertIndex == -1) {
        _requestQueue.add(request);
      } else {
        final list = _requestQueue.toList();
        list.insert(insertIndex, request);
        _requestQueue.clear();
        _requestQueue.addAll(list);
      }
    } else if (request.priority == ThumbnailPriority.normal) {
      // Insert after all high priority, before low
      final insertIndex = _requestQueue.toList().indexWhere(
            (r) => r.priority == ThumbnailPriority.low,
          );
      if (insertIndex == -1) {
        _requestQueue.add(request);
      } else {
        final list = _requestQueue.toList();
        list.insert(insertIndex, request);
        _requestQueue.clear();
        _requestQueue.addAll(list);
      }
    } else {
      // Low priority -> add to end
      _requestQueue.add(request);
    }

    AppLogger.i(
        'Added to thumbnail queue: ${request.videoPath} (priority: ${request.priority})');
  }

  Future<void> _processQueue() async {
    if (_isProcessing) return;
    _isProcessing = true;

    while (_requestQueue.isNotEmpty && _currentConcurrent < _maxConcurrent) {
      // Find next non-cancelled request
      _ThumbnailRequest? request;
      while (_requestQueue.isNotEmpty) {
        final next = _requestQueue.removeFirst();
        if (next.cancellationToken?.isCancelled ?? false) {
          _pendingPaths.remove(next.videoPath);
          next.completer.complete(null);
          AppLogger.i('Cancelled thumbnail request: ${next.videoPath}');
        } else {
          request = next;
          break;
        }
      }

      if (request == null) continue;

      _currentConcurrent++;

      // Process in background
      _generateThumbnailAsync(request);
    }

    _isProcessing = false;
  }

  Future<void> _generateThumbnailAsync(_ThumbnailRequest request) async {
    try {
      // Check cancellation again before heavy work
      if (request.cancellationToken?.isCancelled ?? false) {
        _pendingPaths.remove(request.videoPath);
        request.completer.complete(null);
        _currentConcurrent--;
        _processQueue();
        return;
      }

      AppLogger.i('Generating thumbnail: ${request.videoPath}');

      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: request.videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: request.seekMs,
        quality: 60,
      );

      // Check cancellation after generation
      if (request.cancellationToken?.isCancelled ?? false) {
        _pendingPaths.remove(request.videoPath);
        request.completer.complete(null);
        _currentConcurrent--;
        _processQueue();
        return;
      }

      if (thumbnailData != null && thumbnailData.isNotEmpty) {
        final thumbnailPath = await _getThumbnailPath(request.videoPath);
        final file = File(thumbnailPath);
        await file.writeAsBytes(thumbnailData);

        _thumbnailPathCache.put(request.videoPath, thumbnailPath);
        _pendingPaths.remove(request.videoPath);
        request.completer.complete(thumbnailPath);

        AppLogger.i('Thumbnail generated: ${request.videoPath}');
      } else {
        _pendingPaths.remove(request.videoPath);
        request.completer.complete(null);
      }
    } catch (e, stackTrace) {
      AppLogger.e('Error generating thumbnail for ${request.videoPath}: $e', e,
          stackTrace);
      _pendingPaths.remove(request.videoPath);
      request.completer.complete(null);
    } finally {
      _currentConcurrent--;
      // Process next item
      _processQueue();
    }
  }

  /// Get thumbnail file path (without generating)
  Future<String> _getThumbnailPath(String videoPath) async {
    final thumbnailDir = await _getThumbnailDirectory();
    return '$thumbnailDir/${videoPath.hashCode}.jpg';
  }

  /// Get the thumbnail directory
  Future<String> _getThumbnailDirectory() async {
    final directory = await getTemporaryDirectory();
    final thumbnailDir = Directory('${directory.path}/thumbnails');
    if (!await thumbnailDir.exists()) {
      await thumbnailDir.create(recursive: true);
    }
    return thumbnailDir.path;
  }

  /// Get cached thumbnail path if available
  String? getCachedThumbnail(String videoPath) {
    return _thumbnailPathCache.get(videoPath);
  }

  /// Check if thumbnail is cached
  bool isThumbnailCached(String videoPath) {
    return _thumbnailPathCache.containsKey(videoPath);
  }

  /// Cancel all pending requests
  void cancelAllPending() {
    AppLogger.i('Cancelling all pending thumbnail requests');
    for (final request in _requestQueue) {
      _pendingPaths.remove(request.videoPath);
      request.completer.complete(null);
    }
    _requestQueue.clear();
  }

  /// Clear all cached thumbnails
  Future<void> clearCache() async {
    cancelAllPending();
    _thumbnailPathCache.clear();

    try {
      final thumbnailDir = await _getThumbnailDirectory();
      final dir = Directory(thumbnailDir);
      if (await dir.exists()) {
        await for (final entity in dir.list()) {
          if (entity is File) {
            await entity.delete();
          }
        }
      }
      AppLogger.i('Thumbnail cache cleared');
    } catch (e) {
      AppLogger.e('Error clearing thumbnail cache: $e');
    }
  }

  /// Get queue stats for debugging
  Map<String, dynamic> getStats() {
    return {
      'queueSize': _requestQueue.length,
      'pendingPaths': _pendingPaths.length,
      'concurrent': _currentConcurrent,
      'cacheSize': _thumbnailPathCache.length,
    };
  }
}
