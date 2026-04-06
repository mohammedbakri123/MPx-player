import '../entities/download_item.dart';
import '../entities/download_progress.dart';
import '../entities/video_info.dart';
import '../enums/quality_preference.dart';

abstract class DownloaderRepository {
  Future<VideoInfo?> getVideoInfo(String url);

  Future<String> enqueueDownload(String url, QualityPreference quality,
      {String? customTitle});

  Future<void> cancelDownload(String taskId);

  Future<void> pauseDownload(String taskId);

  Future<void> resumeDownload(String taskId);

  Stream<DownloadProgress> watchProgress(String taskId);

  Future<List<DownloadItem>> getActiveDownloads();

  Future<List<DownloadItem>> getCompletedDownloads();

  Future<DownloadItem?> getDownload(String taskId);

  Future<void> deleteDownload(String taskId);

  Future<int> importShareDownloads();
}
