import 'dart:async';
import 'dart:isolate';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import 'logger_service.dart';

/// Worker pool for concurrent thumbnail generation using isolates
class ThumbnailWorkerPool {
  static const int _workerCount = 3;
  final List<SendPort> _workers = [];
  final Map<String, Completer<String?>> _pendingRequests = {};
  bool _initialized = false;
  int _nextWorkerIndex = 0;

  Future<void> initialize() async {
    if (_initialized) return;

    try {
      AppLogger.i(
          'Initializing thumbnail worker pool with $_workerCount workers');

      for (int i = 0; i < _workerCount; i++) {
        try {
          final receivePort = ReceivePort();
          await Isolate.spawn(
            _workerIsolate,
            receivePort.sendPort,
            onError: receivePort.sendPort,
            onExit: receivePort.sendPort,
          );

          final sendPort = await receivePort.first as SendPort;
          _workers.add(sendPort);

          // Listen for completed work from this worker
          receivePort.listen((message) {
            if (message is _ThumbnailResult) {
              _handleResult(message);
            } else if (message == null) {
              // Worker exited
              AppLogger.w('Thumbnail worker $i exited');
            }
          });
        } catch (e) {
          AppLogger.e('Failed to spawn thumbnail worker $i: $e');
          // Continue with fewer workers
        }
      }

      if (_workers.isNotEmpty) {
        _initialized = true;
        AppLogger.i(
            'Thumbnail worker pool initialized with ${_workers.length} workers');
      } else {
        AppLogger.w('Failed to initialize any thumbnail workers');
      }
    } catch (e, stackTrace) {
      AppLogger.e(
          'Error initializing thumbnail worker pool: $e', e, stackTrace);
    }
  }

  /// Generate thumbnail for video, returns cached result if available
  Future<String?> generateThumbnail(String videoPath,
      {int timeMs = 1000}) async {
    await initialize();

    // Check if already pending
    if (_pendingRequests.containsKey(videoPath)) {
      AppLogger.i('Thumbnail already being generated for: $videoPath');
      return _pendingRequests[videoPath]!.future;
    }

    final completer = Completer<String?>();
    _pendingRequests[videoPath] = completer;

    // Round-robin assignment to workers
    final workerIndex = _nextWorkerIndex % _workers.length;
    _nextWorkerIndex++;

    _workers[workerIndex].send(_ThumbnailRequest(videoPath, timeMs));

    return completer.future;
  }

  void _handleResult(_ThumbnailResult result) {
    final completer = _pendingRequests.remove(result.videoPath);
    if (completer != null) {
      completer.complete(result.thumbnailPath);
    }
  }

  /// Worker isolate entry point
  static void _workerIsolate(SendPort mainSendPort) {
    final receivePort = ReceivePort();
    mainSendPort.send(receivePort.sendPort);

    receivePort.listen((request) async {
      if (request is _ThumbnailRequest) {
        final thumbnailPath =
            await _generateThumbnail(request.videoPath, timeMs: request.timeMs);
        mainSendPort.send(_ThumbnailResult(request.videoPath, thumbnailPath));
      }
    });
  }

  /// Generate thumbnail in isolate
  static Future<String?> _generateThumbnail(String videoPath,
      {required int timeMs}) async {
    try {
      AppLogger.i('Generating thumbnail in isolate for: $videoPath');

      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: timeMs,
        quality: 60, // Reduced from 75 for better performance
      );

      if (thumbnailData == null) {
        AppLogger.w('Failed to generate thumbnail data for: $videoPath');
        return null;
      }

      final thumbnailPath = await _saveThumbnail(thumbnailData, videoPath);
      AppLogger.i('Thumbnail saved: $thumbnailPath');
      return thumbnailPath;
    } catch (e, stackTrace) {
      AppLogger.e(
          'Error generating thumbnail for $videoPath: $e', e, stackTrace);
      return null;
    }
  }

  /// Save thumbnail data to cache directory
  static Future<String> _saveThumbnail(List<int> data, String videoPath) async {
    final cacheDir = await getTemporaryDirectory();
    final thumbnailsDir = Directory('${cacheDir.path}/thumbnails');

    if (!await thumbnailsDir.exists()) {
      await thumbnailsDir.create(recursive: true);
    }

    final fileName = '${videoPath.hashCode}.jpg';
    final file = File('${thumbnailsDir.path}/$fileName');
    await file.writeAsBytes(data);
    return file.path;
  }

  /// Dispose all workers
  void dispose() {
    AppLogger.i('Disposing thumbnail worker pool');
    for (final worker in _workers) {
      worker.send(null); // Signal to terminate
    }
    _workers.clear();
    _pendingRequests.clear();
    _initialized = false;
  }
}

/// Request object sent to worker isolate
class _ThumbnailRequest {
  final String videoPath;
  final int timeMs;
  _ThumbnailRequest(this.videoPath, this.timeMs);
}

/// Result object received from worker isolate
class _ThumbnailResult {
  final String videoPath;
  final String? thumbnailPath;
  _ThumbnailResult(this.videoPath, this.thumbnailPath);
}
