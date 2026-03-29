import '../../domain/entities/download_item.dart';
import '../../domain/enums/download_status.dart';

class DownloadItemModel extends DownloadItem {
  const DownloadItemModel({
    required super.id,
    super.videoId,
    required super.url,
    required super.title,
    super.savePath,
    super.formatSelector,
    required super.status,
    required super.progress,
    required super.addedAt,
    super.completedAt,
    super.errorMessage,
  });

  factory DownloadItemModel.fromEntity(DownloadItem item) {
    return DownloadItemModel(
      id: item.id,
      videoId: item.videoId,
      url: item.url,
      title: item.title,
      savePath: item.savePath,
      formatSelector: item.formatSelector,
      status: item.status,
      progress: item.progress,
      addedAt: item.addedAt,
      completedAt: item.completedAt,
      errorMessage: item.errorMessage,
    );
  }

  factory DownloadItemModel.fromMap(Map<String, dynamic> map) {
    return DownloadItemModel(
      id: map['id'] as String,
      videoId: map['video_id'] as String?,
      url: map['url'] as String,
      title: map['title'] as String,
      savePath: map['save_path'] as String?,
      formatSelector: map['format_selector'] as String?,
      status: DownloadStatus.values.byName(map['status'] as String),
      progress: (map['progress'] as num?)?.toDouble() ?? 0,
      addedAt: DateTime.fromMillisecondsSinceEpoch(map['added_at'] as int),
      completedAt: map['completed_at'] == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(map['completed_at'] as int),
      errorMessage: map['error_message'] as String?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'video_id': videoId,
      'url': url,
      'title': title,
      'save_path': savePath,
      'format_selector': formatSelector,
      'status': status.name,
      'progress': progress,
      'added_at': addedAt.millisecondsSinceEpoch,
      'completed_at': completedAt?.millisecondsSinceEpoch,
      'error_message': errorMessage,
    };
  }
}
