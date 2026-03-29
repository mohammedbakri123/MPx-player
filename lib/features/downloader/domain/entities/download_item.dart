import '../enums/download_status.dart';

class DownloadItem {
  final String id;
  final String? videoId;
  final String url;
  final String title;
  final String? savePath;
  final String? formatSelector;
  final DownloadStatus status;
  final double progress;
  final DateTime addedAt;
  final DateTime? completedAt;
  final String? errorMessage;

  const DownloadItem({
    required this.id,
    this.videoId,
    required this.url,
    required this.title,
    this.savePath,
    this.formatSelector,
    required this.status,
    required this.progress,
    required this.addedAt,
    this.completedAt,
    this.errorMessage,
  });

  DownloadItem copyWith({
    String? id,
    String? videoId,
    String? url,
    String? title,
    String? savePath,
    String? formatSelector,
    DownloadStatus? status,
    double? progress,
    DateTime? addedAt,
    DateTime? completedAt,
    String? errorMessage,
    bool clearCompletedAt = false,
    bool clearErrorMessage = false,
  }) {
    return DownloadItem(
      id: id ?? this.id,
      videoId: videoId ?? this.videoId,
      url: url ?? this.url,
      title: title ?? this.title,
      savePath: savePath ?? this.savePath,
      formatSelector: formatSelector ?? this.formatSelector,
      status: status ?? this.status,
      progress: progress ?? this.progress,
      addedAt: addedAt ?? this.addedAt,
      completedAt: clearCompletedAt ? null : (completedAt ?? this.completedAt),
      errorMessage:
          clearErrorMessage ? null : (errorMessage ?? this.errorMessage),
    );
  }
}
