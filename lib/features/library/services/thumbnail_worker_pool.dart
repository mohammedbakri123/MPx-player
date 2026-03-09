import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../../../core/services/logger_service.dart';
import 'thumbnail_cache.dart';

String? _persistentThumbnailDir;

Future<String> getPersistentThumbnailDirectory() async {
  if (_persistentThumbnailDir != null) return _persistentThumbnailDir!;
  final appDir = await getApplicationDocumentsDirectory();
  final thumbnailsDir = Directory('${appDir.path}/thumbnails');
  if (!await thumbnailsDir.exists()) {
    await thumbnailsDir.create(recursive: true);
  }
  _persistentThumbnailDir = thumbnailsDir.path;
  return _persistentThumbnailDir!;
}

/// Parameters for thumbnail generation in isolate
class _ThumbnailParams {
  final String videoPath;
  final String thumbnailDir;
  final int? timeMs;
  final bool smartTimestamp;
  final int? durationMs;

  _ThumbnailParams({
    required this.videoPath,
    required this.thumbnailDir,
    this.timeMs,
    this.smartTimestamp = true,
    this.durationMs,
  });
}

/// Isolate function for thumbnail generation
Future<String?> _generateThumbnailIsolate(_ThumbnailParams params) async {
  try {
    final fileName = '${params.videoPath.hashCode.abs()}.jpg';
    final thumbnailPath = '${params.thumbnailDir}/$fileName';

    final existingFile = File(thumbnailPath);
    if (await existingFile.exists()) {
      return thumbnailPath;
    }

    int finalTimeMs = params.timeMs ?? 1000;

    if (params.smartTimestamp &&
        params.timeMs == null &&
        params.durationMs != null &&
        params.durationMs! > 10000) {
      finalTimeMs = (params.durationMs! * 0.1).toInt();
    }

    final Uint8List? thumbnailData = await VideoThumbnail.thumbnailData(
      video: params.videoPath,
      imageFormat: ImageFormat.JPEG,
      maxHeight: 200,
      maxWidth: 300,
      timeMs: finalTimeMs,
      quality: 60,
    );

    if (thumbnailData == null || thumbnailData.isEmpty) {
      return null;
    }

    final file = File(thumbnailPath);
    await file.writeAsBytes(thumbnailData);

    return thumbnailPath;
  } catch (e) {
    debugPrint('Error in _generateThumbnailIsolate: $e');
    return null;
  }
}

class ThumbnailWorkerPool {
  static final ThumbnailWorkerPool _instance = ThumbnailWorkerPool._internal();
  factory ThumbnailWorkerPool() => _instance;
  ThumbnailWorkerPool._internal();

  final Map<String, Completer<String?>> _pendingRequests = {};
  bool _initialized = false;
  String? _thumbnailDir;

  Future<void> initialize() async {
    if (_initialized) return;

    try {
      _thumbnailDir = await getPersistentThumbnailDirectory();
      AppLogger.i('Using persistent thumbnail directory: $_thumbnailDir');
      _initialized = true;
    } catch (e, stackTrace) {
      AppLogger.e(
          'Error initializing thumbnail worker pool: $e', e, stackTrace);
    }
  }

  Future<String?> generateThumbnail(
    String videoPath, {
    int? timeMs,
    bool smartTimestamp = true,
    int? durationMs,
  }) async {
    await initialize();

    if (_pendingRequests.containsKey(videoPath)) {
      return _pendingRequests[videoPath]!.future;
    }

    final completer = Completer<String?>();
    _pendingRequests[videoPath] = completer;

    try {
      // Check disk first
      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final thumbnailPath = '$_thumbnailDir/$fileName';
      if (await File(thumbnailPath).exists()) {
        _pendingRequests.remove(videoPath);
        completer.complete(thumbnailPath);
        
        // Asynchronously update memory cache
        unawaited(ThumbnailCache().putPath(videoPath, thumbnailPath));
        return thumbnailPath;
      }

      // Generate in background isolate using compute
      final path = await compute(
        _generateThumbnailIsolate,
        _ThumbnailParams(
          videoPath: videoPath,
          thumbnailDir: _thumbnailDir!,
          timeMs: timeMs,
          smartTimestamp: smartTimestamp,
          durationMs: durationMs,
        ),
      );

      _pendingRequests.remove(videoPath);
      completer.complete(path);
      
      if (path != null) {
        AppLogger.i('Thumbnail generated in background: $path');
        // Asynchronously update memory cache
        unawaited(ThumbnailCache().putPath(videoPath, path));
      }
      
      return path;
    } catch (e) {
      AppLogger.e('Error generating thumbnail: $e');
      _pendingRequests.remove(videoPath);
      completer.complete(null);
      return null;
    }
  }

  void dispose() {
    _pendingRequests.clear();
    _initialized = false;
  }
}
