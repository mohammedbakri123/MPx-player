import 'dart:async';

import 'package:flutter/foundation.dart';

import '../data/repositories/downloader_repository_impl.dart';
import '../domain/entities/download_progress.dart';
import '../domain/enums/quality_preference.dart';
import '../domain/repositories/downloader_repository.dart';
import '../services/downloader_settings_service.dart';
import 'downloader_ui_state.dart';

class DownloaderController extends ChangeNotifier {
  DownloaderController({DownloaderRepository? repository})
      : _repository = repository ?? DownloaderRepositoryImpl(),
        _state = DownloaderUiState(
          selectedQuality: DownloaderSettingsService.defaultQuality,
        ) {
    unawaited(refreshDownloads());
  }

  final DownloaderRepository _repository;
  final Map<String, StreamSubscription<DownloadProgress>> _subscriptions =
      <String, StreamSubscription<DownloadProgress>>{};

  DownloaderUiState _state;

  DownloaderUiState get state => _state;

  Future<void> refreshDownloads() async {
    await _repository.importShareDownloads();
    final activeDownloads = await _repository.getActiveDownloads();
    final completedDownloads = await _repository.getCompletedDownloads();
    for (final item in activeDownloads) {
      _subscribeToProgress(item.id);
    }
    _setState(
      _state.copyWith(
        activeDownloads: activeDownloads,
        completedDownloads: completedDownloads,
      ),
    );
  }

  Future<void> fetchVideoInfo(String url) async {
    _setState(
      _state.copyWith(
        isLoading: true,
        clearErrorMessage: true,
      ),
    );

    try {
      final videoInfo = await _repository.getVideoInfo(url);
      _setState(
        _state.copyWith(
          isLoading: false,
          clearErrorMessage: videoInfo != null,
          videoInfo: videoInfo,
          errorMessage: videoInfo == null
              ? 'Could not load metadata for this URL.'
              : null,
        ),
      );
    } catch (error) {
      _setState(
        _state.copyWith(
          isLoading: false,
          errorMessage: error.toString(),
        ),
      );
    }
  }

  Future<void> startDownload(String url, {QualityPreference? quality}) async {
    _setState(
      _state.copyWith(
        isBusy: true,
        clearErrorMessage: true,
      ),
    );

    try {
      final taskId = await _repository.enqueueDownload(
        url,
        quality ?? _state.selectedQuality,
      );
      _subscribeToProgress(taskId);
      await refreshDownloads();
      _setState(
        _state.copyWith(
          isBusy: false,
          activeTaskId: taskId,
          clearVideoInfo: true,
        ),
      );
    } catch (error) {
      _setState(
        _state.copyWith(
          isBusy: false,
          errorMessage: error.toString(),
        ),
      );
    }
  }

  Future<void> cancelDownload(String taskId) async {
    await _repository.cancelDownload(taskId);
    await refreshDownloads();
  }

  Future<void> pauseDownload(String taskId) async {
    await _repository.pauseDownload(taskId);
    await refreshDownloads();
  }

  Future<void> resumeDownload(String taskId) async {
    await _repository.resumeDownload(taskId);
    _subscribeToProgress(taskId);
    await refreshDownloads();
  }

  Future<void> retryDownload(String taskId) async {
    final item = await _repository.getDownload(taskId);
    if (item == null) return;
    await _repository.deleteDownload(taskId);
    _subscriptions.remove(taskId)?.cancel();
    await startDownload(item.url, quality: null);
  }

  Future<void> deleteDownload(String taskId) async {
    await _repository.deleteDownload(taskId);
    await _subscriptions.remove(taskId)?.cancel();
    await refreshDownloads();
  }

  void selectQuality(QualityPreference quality) {
    unawaited(DownloaderSettingsService.setDefaultQuality(quality));
    _setState(_state.copyWith(selectedQuality: quality));
  }

  void _subscribeToProgress(String taskId) {
    if (_subscriptions.containsKey(taskId)) {
      return;
    }

    _subscriptions[taskId] =
        _repository.watchProgress(taskId).listen((_) async {
      await refreshDownloads();
    });
  }

  void _setState(DownloaderUiState state) {
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    for (final subscription in _subscriptions.values) {
      subscription.cancel();
    }
    super.dispose();
  }
}
