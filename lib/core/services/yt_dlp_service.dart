import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';

import '../../features/downloader/data/models/video_info_model.dart';
import '../../features/downloader/domain/entities/download_progress.dart';
import '../../features/downloader/domain/enums/download_status.dart';
import '../../features/downloader/services/downloader_settings_service.dart';
import 'binary_manager.dart';
import 'downloader_platform_service.dart';

class YtDlpService {
  YtDlpService({
    BinaryManager? binaryManager,
    DownloaderPlatformService? platformService,
  })  : _binaryManager = binaryManager ?? BinaryManager.instance,
        _platformService =
            platformService ?? DownloaderPlatformService.instance;

  final BinaryManager _binaryManager;
  final DownloaderPlatformService _platformService;

  Future<VideoInfoModel?> fetchInfo(String url) async {
    final ytDlpPath = await _binaryManager.getYtDlpPath();
    if (ytDlpPath == null) {
      throw StateError('yt-dlp binary is not available yet.');
    }

    Map<String, dynamic> payload;
    try {
      payload = await _platformService.fetchVideoInfo(
        ytDlpPath: ytDlpPath,
        url: url,
        cookiesPath: DownloaderSettingsService.cookiesPath,
      );
    } on PlatformException catch (error) {
      throw StateError(_normalizePlatformError(error));
    }
    final rawJson = payload['json'] as String?;
    if (rawJson == null || rawJson.isEmpty) {
      return null;
    }
    return VideoInfoModel.fromJson(
      jsonDecode(rawJson) as Map<String, dynamic>,
    );
  }

  Stream<DownloadProgress> startDownload({
    required String taskId,
    required String url,
    required String outputPath,
    required String formatSelector,
    String? cookiesPath,
  }) async* {
    final ytDlpPath = await _binaryManager.getYtDlpPath();
    if (ytDlpPath == null) {
      throw StateError('yt-dlp binary is not available yet.');
    }

    final ffmpegPath = await _binaryManager.getFfmpegPath();
    try {
      await _platformService.startDownload(
        taskId: taskId,
        ytDlpPath: ytDlpPath,
        ffmpegPath: ffmpegPath,
        cookiesPath: cookiesPath,
        url: url,
        outputPath: outputPath,
        formatSelector: formatSelector,
      );
    } on PlatformException catch (error) {
      throw StateError(_normalizePlatformError(error));
    }

    yield* _platformService.events
        .where((event) => event['taskId'] == taskId)
        .map(_mapProgressEvent);
  }

  Future<void> cancelDownload(String taskId) {
    return _platformService.cancelDownload(taskId);
  }

  String _normalizePlatformError(PlatformException error) {
    final message = error.message ?? error.details?.toString() ?? error.code;
    return 'Downloader failed: $message';
  }

  DownloadProgress _mapProgressEvent(Map<String, dynamic> event) {
    final statusName =
        event['status'] as String? ?? DownloadStatus.downloading.name;
    return DownloadProgress(
      taskId: event['taskId'] as String,
      progress: ((event['progress'] as num?)?.toDouble() ?? 0).clamp(0, 1),
      status: DownloadStatus.values
          .where((value) => value.name == statusName)
          .first,
      speedText: event['speedText'] as String?,
      etaText: event['etaText'] as String?,
      logLine: event['logLine'] as String?,
      filePath: event['filePath'] as String?,
    );
  }
}
