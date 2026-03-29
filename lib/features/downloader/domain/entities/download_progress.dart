import '../enums/download_status.dart';

class DownloadProgress {
  final String taskId;
  final double progress;
  final DownloadStatus status;
  final String? speedText;
  final String? etaText;
  final String? logLine;
  final String? filePath;

  const DownloadProgress({
    required this.taskId,
    required this.progress,
    required this.status,
    this.speedText,
    this.etaText,
    this.logLine,
    this.filePath,
  });
}
