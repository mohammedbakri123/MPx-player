import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';
import 'package:mpx/core/services/downloader_platform_service.dart';

import '../../domain/entities/download_item.dart';
import '../../domain/entities/download_progress.dart';
import '../../domain/entities/video_info.dart';
import '../../domain/enums/download_status.dart';
import '../../domain/enums/quality_preference.dart';
import '../../domain/repositories/downloader_repository.dart';
import '../../services/downloader_settings_service.dart';
import '../datasources/local_download_datasource.dart';
import '../datasources/yt_dlp_remote_datasource.dart';

class DownloaderRepositoryImpl implements DownloaderRepository {
  DownloaderRepositoryImpl({
    LocalDownloadDataSource? localDataSource,
    YtDlpRemoteDataSource? remoteDataSource,
    Uuid? uuid,
  })  : _localDataSource = localDataSource ?? LocalDownloadDataSource(),
        _remoteDataSource = remoteDataSource ?? YtDlpRemoteDataSource(),
        _uuid = uuid ?? const Uuid();

  final LocalDownloadDataSource _localDataSource;
  final YtDlpRemoteDataSource _remoteDataSource;
  final Uuid _uuid;
  final DownloaderPlatformService _platform =
      DownloaderPlatformService.instance;
  final Map<String, StreamController<DownloadProgress>> _progressControllers =
      <String, StreamController<DownloadProgress>>{};
  final Map<String, StreamSubscription<DownloadProgress>> _activeTasks =
      <String, StreamSubscription<DownloadProgress>>{};

  @override
  Future<VideoInfo?> getVideoInfo(String url) {
    return _remoteDataSource.fetchVideoInfo(url);
  }

  @override
  Future<String> enqueueDownload(String url, QualityPreference quality) async {
    final taskId = _uuid.v4();
    final videoInfo = await _remoteDataSource.fetchVideoInfo(url);
    final outputPath = await _buildOutputPath(
      taskId,
      videoInfo?.title,
      quality,
    );
    final item = DownloadItem(
      id: taskId,
      videoId: videoInfo?.id,
      url: url,
      title: videoInfo?.title ?? 'Pending download',
      savePath: outputPath,
      formatSelector: quality.formatSelector,
      status: DownloadStatus.queued,
      progress: 0,
      addedAt: DateTime.now(),
    );
    await _localDataSource.upsertDownload(item);
    await _startRemoteDownload(item);
    return taskId;
  }

  @override
  Future<void> cancelDownload(String taskId) async {
    await _remoteDataSource.cancelDownload(taskId);
    await _activeTasks.remove(taskId)?.cancel();
    await _updateStatus(taskId, DownloadStatus.cancelled);
    _emitProgress(
      DownloadProgress(
        taskId: taskId,
        progress: 0,
        status: DownloadStatus.cancelled,
        logLine: 'Download cancelled',
      ),
    );
  }

  @override
  Future<void> pauseDownload(String taskId) async {
    await _remoteDataSource.cancelDownload(taskId);
    await _activeTasks.remove(taskId)?.cancel();
    await _updateStatus(taskId, DownloadStatus.paused);
    _emitProgress(
      DownloadProgress(
        taskId: taskId,
        progress: 0,
        status: DownloadStatus.paused,
        logLine: 'Download paused',
      ),
    );
  }

  @override
  Future<void> resumeDownload(String taskId) async {
    final item = await _localDataSource.getDownload(taskId);
    if (item == null) {
      return;
    }
    await _updateStatus(taskId, DownloadStatus.queued);
    await _startRemoteDownload(item.copyWith(status: DownloadStatus.queued));
    _emitProgress(
      DownloadProgress(
        taskId: taskId,
        progress: 0,
        status: DownloadStatus.queued,
        logLine: 'Download resume requested',
      ),
    );
  }

  @override
  Stream<DownloadProgress> watchProgress(String taskId) {
    return _progressControllers
        .putIfAbsent(
            taskId, () => StreamController<DownloadProgress>.broadcast())
        .stream;
  }

  @override
  Future<List<DownloadItem>> getActiveDownloads() {
    return _localDataSource.getActiveDownloads();
  }

  @override
  Future<List<DownloadItem>> getCompletedDownloads() {
    return _localDataSource.getCompletedDownloads();
  }

  @override
  Future<void> deleteDownload(String taskId) async {
    await _activeTasks.remove(taskId)?.cancel();
    await _localDataSource.deleteDownload(taskId);
    await _progressControllers.remove(taskId)?.close();
  }

  @override
  Future<int> importShareDownloads() async {
    final entries = await _platform.consumeShareDownloads();
    int count = 0;
    for (final entry in entries) {
      final url = entry['url'] as String? ?? '';
      final title = entry['title'] as String? ?? 'Shared Video';
      final savePath = entry['savePath'] as String? ?? '';
      final success = entry['success'] as bool? ?? false;
      final error = entry['error'] as String?;

      if (url.isEmpty) continue;

      final taskId = _uuid.v4();
      final item = DownloadItem(
        id: taskId,
        url: url,
        title: title,
        savePath: success && savePath.isNotEmpty ? savePath : null,
        status: success ? DownloadStatus.completed : DownloadStatus.failed,
        progress: success ? 1 : 0,
        completedAt: success ? DateTime.now() : null,
        errorMessage: error?.isNotEmpty == true ? error : null,
        addedAt: DateTime.now(),
      );
      await _localDataSource.upsertDownload(item);
      count++;
    }
    return count;
  }

  Future<void> _startRemoteDownload(DownloadItem item) async {
    await _updateStatus(item.id, DownloadStatus.downloading);
    final stream = _remoteDataSource.startDownload(
      taskId: item.id,
      url: item.url,
      outputPath: item.savePath ?? '',
      formatSelector:
          item.formatSelector ?? QualityPreference.auto.formatSelector,
      cookiesPath: DownloaderSettingsService.cookiesPath,
    );

    _activeTasks[item.id]?.cancel();
    _activeTasks[item.id] = stream.listen(
      (progress) async {
        await _persistProgress(item.id, progress);
        _emitProgress(progress);
      },
      onError: (Object error) async {
        final current = await _localDataSource.getDownload(item.id);
        if (current != null) {
          await _localDataSource.upsertDownload(
            current.copyWith(
              status: DownloadStatus.failed,
              errorMessage: error.toString(),
            ),
          );
        }
        _emitProgress(
          DownloadProgress(
            taskId: item.id,
            progress: 0,
            status: DownloadStatus.failed,
            logLine: error.toString(),
          ),
        );
      },
      onDone: () {
        _activeTasks.remove(item.id);
      },
      cancelOnError: false,
    );
  }

  Future<void> _persistProgress(
      String taskId, DownloadProgress progress) async {
    final current = await _localDataSource.getDownload(taskId);
    if (current == null) {
      return;
    }
    await _localDataSource.upsertDownload(
      current.copyWith(
        savePath: progress.filePath ?? current.savePath,
        progress: progress.progress,
        status: progress.status,
        completedAt:
            progress.status == DownloadStatus.completed ? DateTime.now() : null,
        clearCompletedAt: progress.status != DownloadStatus.completed,
        errorMessage: progress.status == DownloadStatus.failed
            ? progress.logLine
            : current.errorMessage,
      ),
    );

    if (progress.status == DownloadStatus.completed) {
      final latest = await _localDataSource.getDownload(taskId);
      final sourcePath = progress.filePath ?? latest?.savePath;
      if (latest != null && sourcePath != null) {
        try {
          final exportedPath = await _platform.exportDownload(sourcePath);
          if (exportedPath != null && exportedPath.isNotEmpty) {
            await _localDataSource.upsertDownload(
              latest.copyWith(
                savePath: exportedPath,
                progress: 1,
                status: DownloadStatus.completed,
                completedAt: DateTime.now(),
                clearErrorMessage: true,
              ),
            );
          }
        } catch (error) {
          await _localDataSource.upsertDownload(
            latest.copyWith(
              status: DownloadStatus.failed,
              errorMessage: error.toString(),
              clearCompletedAt: true,
            ),
          );
          _emitProgress(
            DownloadProgress(
              taskId: taskId,
              progress: latest.progress,
              status: DownloadStatus.failed,
              logLine: error.toString(),
            ),
          );
        }
      }
    }
  }

  Future<String> _buildOutputPath(
    String taskId,
    String? title,
    QualityPreference quality,
  ) async {
    final supportDir = await getApplicationSupportDirectory();
    final downloadsDir =
        Directory(p.join(supportDir.path, 'downloads', taskId));
    if (!await downloadsDir.exists()) {
      await downloadsDir.create(recursive: true);
    }

    final safeTitle = _sanitizeFileName(title ?? 'download_$taskId');
    return p.join(downloadsDir.path, '$safeTitle.%(ext)s');
  }

  String _sanitizeFileName(String value) {
    return value.replaceAll(RegExp(r'[^a-zA-Z0-9._ -]'), '_').trim();
  }

  Future<void> _updateStatus(String taskId, DownloadStatus status) async {
    final item = await _localDataSource.getDownload(taskId);
    if (item == null) {
      return;
    }

    await _localDataSource.upsertDownload(
      item.copyWith(
        status: status,
        completedAt: status == DownloadStatus.completed ? DateTime.now() : null,
        clearCompletedAt: status != DownloadStatus.completed,
      ),
    );
  }

  void _emitProgress(DownloadProgress progress) {
    final controller = _progressControllers.putIfAbsent(
      progress.taskId,
      () => StreamController<DownloadProgress>.broadcast(),
    );
    if (!controller.isClosed) {
      controller.add(progress);
    }
  }
}
