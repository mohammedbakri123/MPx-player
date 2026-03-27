import 'package:mpx/core/services/yt_dlp_service.dart';

import '../../domain/entities/download_progress.dart';
import '../models/video_info_model.dart';

class YtDlpRemoteDataSource {
  YtDlpRemoteDataSource({YtDlpService? service})
      : _service = service ?? YtDlpService();

  final YtDlpService _service;

  Future<VideoInfoModel?> fetchVideoInfo(String url) async {
    return _service.fetchInfo(url);
  }

  Stream<DownloadProgress> startDownload({
    required String taskId,
    required String url,
    required String outputPath,
    required String formatSelector,
    String? cookiesPath,
  }) {
    return _service.startDownload(
      taskId: taskId,
      url: url,
      outputPath: outputPath,
      formatSelector: formatSelector,
      cookiesPath: cookiesPath,
    );
  }

  Future<void> cancelDownload(String taskId) {
    return _service.cancelDownload(taskId);
  }
}
