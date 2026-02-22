import 'package:shared_preferences/shared_preferences.dart';
import '../../../core/services/logger_service.dart';

class ScanMetadataService {
  static const String _keyLastScanTimestamp = 'last_scan_timestamp';
  static const String _keyLastFullScanTimestamp = 'last_full_scan_timestamp';
  static const String _keyTotalVideos = 'total_videos_count';
  static const String _keyTotalFolders = 'total_folders_count';
  static const String _keyLastScanDuration = 'last_scan_duration_ms';
  static const String _keyScanVersion = 'scan_version';

  static const int currentScanVersion = 1;

  static Future<DateTime?> getLastScanTimestamp() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final timestamp = prefs.getInt(_keyLastScanTimestamp);
      if (timestamp != null) {
        return DateTime.fromMillisecondsSinceEpoch(timestamp);
      }
    } catch (e) {
      AppLogger.e('Failed to get last scan timestamp: $e');
    }
    return null;
  }

  static Future<DateTime?> getLastFullScanTimestamp() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final timestamp = prefs.getInt(_keyLastFullScanTimestamp);
      if (timestamp != null) {
        return DateTime.fromMillisecondsSinceEpoch(timestamp);
      }
    } catch (e) {
      AppLogger.e('Failed to get last full scan timestamp: $e');
    }
    return null;
  }

  static Future<void> setLastScanTimestamp(DateTime timestamp) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(
          _keyLastScanTimestamp, timestamp.millisecondsSinceEpoch);
      AppLogger.d('Updated last scan timestamp: $timestamp');
    } catch (e) {
      AppLogger.e('Failed to set last scan timestamp: $e');
    }
  }

  static Future<void> setLastFullScanTimestamp(DateTime timestamp) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(
          _keyLastFullScanTimestamp, timestamp.millisecondsSinceEpoch);
      AppLogger.d('Updated last full scan timestamp: $timestamp');
    } catch (e) {
      AppLogger.e('Failed to set last full scan timestamp: $e');
    }
  }

  static Future<void> updateScanStats({
    required int videoCount,
    required int folderCount,
    required int durationMs,
  }) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await Future.wait([
        prefs.setInt(_keyTotalVideos, videoCount),
        prefs.setInt(_keyTotalFolders, folderCount),
        prefs.setInt(_keyLastScanDuration, durationMs),
      ]);
    } catch (e) {
      AppLogger.e('Failed to update scan stats: $e');
    }
  }

  static Future<Map<String, dynamic>> getScanStats() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return {
        'totalVideos': prefs.getInt(_keyTotalVideos) ?? 0,
        'totalFolders': prefs.getInt(_keyTotalFolders) ?? 0,
        'lastScanDuration': prefs.getInt(_keyLastScanDuration) ?? 0,
        'lastScanTimestamp': await getLastScanTimestamp(),
        'lastFullScanTimestamp': await getLastFullScanTimestamp(),
      };
    } catch (e) {
      AppLogger.e('Failed to get scan stats: $e');
      return {};
    }
  }

  static Future<bool> shouldDoFullScan(
      {Duration maxIncrementalAge = const Duration(hours: 24)}) async {
    final lastFullScan = await getLastFullScanTimestamp();
    if (lastFullScan == null) return true;

    final timeSinceFullScan = DateTime.now().difference(lastFullScan);
    return timeSinceFullScan > maxIncrementalAge;
  }

  static Future<int> getScanVersion() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getInt(_keyScanVersion) ?? 0;
    } catch (e) {
      return 0;
    }
  }

  static Future<void> setScanVersion(int version) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(_keyScanVersion, version);
    } catch (e) {
      AppLogger.e('Failed to set scan version: $e');
    }
  }

  static Future<void> clearScanMetadata() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await Future.wait([
        prefs.remove(_keyLastScanTimestamp),
        prefs.remove(_keyLastFullScanTimestamp),
        prefs.remove(_keyTotalVideos),
        prefs.remove(_keyTotalFolders),
        prefs.remove(_keyLastScanDuration),
      ]);
      AppLogger.i('Scan metadata cleared');
    } catch (e) {
      AppLogger.e('Failed to clear scan metadata: $e');
    }
  }
}
