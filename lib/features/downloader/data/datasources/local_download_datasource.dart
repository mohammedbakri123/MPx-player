import 'package:mpx/core/database/app_database.dart';

import '../../domain/entities/download_item.dart';
import '../../domain/enums/download_status.dart';

class LocalDownloadDataSource {
  LocalDownloadDataSource({AppDatabase? database})
      : _database = database ?? AppDatabase();

  final AppDatabase _database;

  Future<void> upsertDownload(DownloadItem item) async {
    await _database.upsertDownloadItem(item);
  }

  Future<DownloadItem?> getDownload(String taskId) async {
    return _database.getDownloadItem(taskId);
  }

  Future<List<DownloadItem>> getActiveDownloads() async {
    return _database.getDownloadsByStatuses(const <DownloadStatus>[
      DownloadStatus.queued,
      DownloadStatus.downloading,
      DownloadStatus.paused,
    ]);
  }

  Future<List<DownloadItem>> getCompletedDownloads() async {
    return _database.getDownloadsByStatuses(const <DownloadStatus>[
      DownloadStatus.completed,
      DownloadStatus.failed,
      DownloadStatus.cancelled,
    ]);
  }

  Future<void> deleteDownload(String taskId) async {
    await _database.deleteDownloadItem(taskId);
  }
}
