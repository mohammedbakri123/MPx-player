import 'dart:async';
import 'package:sqflite/sqflite.dart';
import '../../services/logger_service.dart';

/// Database operations for user volume settings
mixin VolumeDatabaseOperations {
  Future<Database> get database;

  /// Get saved volume level (0.0 to 100.0)
  /// Returns null if no volume setting exists
  Future<double?> getSavedVolume() async {
    final db = await database;
    final maps = await db.query(
      'volume_settings',
      columns: ['volume_level'],
      where: 'id = ?',
      whereArgs: ['master_volume'],
      limit: 1,
    );

    if (maps.isEmpty) return null;
    return (maps.first['volume_level'] as num).toDouble();
  }

  /// Save volume level
  /// [volume] - Volume level from 0.0 to 100.0
  Future<void> saveVolume(double volume) async {
    final db = await database;
    await db.insert(
      'volume_settings',
      {
        'id': 'master_volume',
        'volume_level': volume.clamp(0.0, 100.0),
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    AppLogger.d('Volume saved: ${volume.toStringAsFixed(1)}');
  }

  /// Delete saved volume setting
  Future<void> clearVolume() async {
    final db = await database;
    await db.delete(
      'volume_settings',
      where: 'id = ?',
      whereArgs: ['master_volume'],
    );
    AppLogger.i('Volume setting cleared');
  }

  /// Get last volume update timestamp
  Future<int?> getVolumeLastUpdated() async {
    final db = await database;
    final maps = await db.query(
      'volume_settings',
      columns: ['updated_at'],
      where: 'id = ?',
      whereArgs: ['master_volume'],
      limit: 1,
    );

    if (maps.isEmpty) return null;
    return maps.first['updated_at'] as int;
  }
}
