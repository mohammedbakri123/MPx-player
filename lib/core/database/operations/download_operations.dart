import 'package:sqflite/sqflite.dart';

import '../../../features/downloader/data/models/download_item_model.dart';
import '../../../features/downloader/domain/entities/download_item.dart';
import '../../../features/downloader/domain/enums/download_status.dart';
import '../../services/logger_service.dart';

mixin DownloadDatabaseOperations {
  Future<Database> get database;

  Future<void> upsertDownloadItem(DownloadItem item) async {
    final db = await database;
    final model = DownloadItemModel.fromEntity(item);
    await db.insert(
      'downloads',
      model.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<DownloadItem?> getDownloadItem(String taskId) async {
    final db = await database;
    final rows = await db.query(
      'downloads',
      where: 'id = ?',
      whereArgs: [taskId],
      limit: 1,
    );
    if (rows.isEmpty) {
      return null;
    }
    return DownloadItemModel.fromMap(rows.first);
  }

  Future<List<DownloadItem>> getDownloadsByStatuses(
    List<DownloadStatus> statuses,
  ) async {
    if (statuses.isEmpty) {
      return const <DownloadItem>[];
    }

    final db = await database;
    final placeholders = List<String>.filled(statuses.length, '?').join(', ');
    final rows = await db.query(
      'downloads',
      where: 'status IN ($placeholders)',
      whereArgs: statuses.map((status) => status.name).toList(growable: false),
      orderBy: 'added_at DESC',
    );
    return rows.map(DownloadItemModel.fromMap).toList(growable: false);
  }

  Future<void> deleteDownloadItem(String taskId) async {
    final db = await database;
    await db.delete('downloads', where: 'id = ?', whereArgs: [taskId]);
  }

  Future<void> clearDownloads() async {
    final db = await database;
    await db.delete('downloads');
    AppLogger.i('All downloads deleted from database');
  }
}
