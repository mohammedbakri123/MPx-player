import 'dart:async';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../../../core/services/logger_service.dart';

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
      final path = await _generateThumbnailDirect(
        videoPath,
        timeMs: timeMs,
        smartTimestamp: smartTimestamp,
        durationMs: durationMs,
      );

      _pendingRequests.remove(videoPath);
      completer.complete(path);
      return path;
    } catch (e) {
      AppLogger.e('Error generating thumbnail: $e');
      _pendingRequests.remove(videoPath);
      completer.complete(null);
      return null;
    }
  }

  Future<String?> _generateThumbnailDirect(
    String videoPath, {
    int? timeMs,
    bool smartTimestamp = true,
    int? durationMs,
  }) async {
    try {
      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final thumbnailPath = '$_thumbnailDir/$fileName';

      final existingFile = File(thumbnailPath);
      if (await existingFile.exists()) {
        return thumbnailPath;
      }

      int finalTimeMs = timeMs ?? 1000;

      if (smartTimestamp &&
          timeMs == null &&
          durationMs != null &&
          durationMs > 10000) {
        finalTimeMs = (durationMs * 0.1).toInt();
      }

      final thumbnailData = await VideoThumbnail.thumbnailData(
        video: videoPath,
        imageFormat: ImageFormat.JPEG,
        maxHeight: 200,
        maxWidth: 300,
        timeMs: finalTimeMs,
        quality: 60,
      );

      if (thumbnailData == null || thumbnailData.isEmpty) {
        AppLogger.w('Failed to generate thumbnail data for: $videoPath');
        return null;
      }

      final file = File(thumbnailPath);
      await file.writeAsBytes(thumbnailData);

      AppLogger.i('Thumbnail generated: $thumbnailPath');
      return thumbnailPath;
    } catch (e) {
      AppLogger.e('Error in _generateThumbnailDirect: $e');
      return null;
    }
  }

  void dispose() {
    _pendingRequests.clear();
    _initialized = false;
  }
}
