import '../domain/entities/download_item.dart';
import '../domain/entities/video_info.dart';
import '../domain/enums/quality_preference.dart';

class DownloaderUiState {
  final bool isLoading;
  final bool isBusy;
  final String? activeTaskId;
  final String? errorMessage;
  final VideoInfo? videoInfo;
  final QualityPreference selectedQuality;
  final List<DownloadItem> activeDownloads;
  final List<DownloadItem> completedDownloads;

  const DownloaderUiState({
    this.isLoading = false,
    this.isBusy = false,
    this.activeTaskId,
    this.errorMessage,
    this.videoInfo,
    this.selectedQuality = QualityPreference.auto,
    this.activeDownloads = const <DownloadItem>[],
    this.completedDownloads = const <DownloadItem>[],
  });

  DownloaderUiState copyWith({
    bool? isLoading,
    bool? isBusy,
    String? activeTaskId,
    String? errorMessage,
    VideoInfo? videoInfo,
    QualityPreference? selectedQuality,
    List<DownloadItem>? activeDownloads,
    List<DownloadItem>? completedDownloads,
    bool clearActiveTaskId = false,
    bool clearErrorMessage = false,
    bool clearVideoInfo = false,
  }) {
    return DownloaderUiState(
      isLoading: isLoading ?? this.isLoading,
      isBusy: isBusy ?? this.isBusy,
      activeTaskId:
          clearActiveTaskId ? null : (activeTaskId ?? this.activeTaskId),
      errorMessage:
          clearErrorMessage ? null : (errorMessage ?? this.errorMessage),
      videoInfo: clearVideoInfo ? null : (videoInfo ?? this.videoInfo),
      selectedQuality: selectedQuality ?? this.selectedQuality,
      activeDownloads: activeDownloads ?? this.activeDownloads,
      completedDownloads: completedDownloads ?? this.completedDownloads,
    );
  }
}
