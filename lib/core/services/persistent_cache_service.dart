import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../features/library/domain/entities/video_folder.dart';

class PersistentCacheService {
  static const String _cacheKey = 'video_folders_cache';
  static const String _timestampKey = 'video_folders_timestamp';
  static const String _fileMetadataKey = 'file_metadata_cache';

  // Save video folders to persistent cache
  static Future<void> saveToCache(List<VideoFolder> folders) async {
    final prefs = await SharedPreferences.getInstance();

    // Convert folders to JSON
    final jsonList = folders.map((folder) => folder.toJson()).toList();
    final jsonString = jsonEncode(jsonList);

    await prefs.setString(_cacheKey, jsonString);
    await prefs.setInt(_timestampKey, DateTime.now().millisecondsSinceEpoch);
  }

  // Load video folders from persistent cache
  static Future<List<VideoFolder>?> loadFromCache() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_cacheKey);

    if (jsonString == null) {
      return null;
    }

    try {
      final jsonList = jsonDecode(jsonString) as List;
      final folders = jsonList
          .map((json) => VideoFolder.fromJson(json as Map<String, dynamic>))
          .toList();
      return folders;
    } catch (e) {
      // If there's an error decoding, return null to trigger a fresh scan
      return null;
    }
  }

  // Get the timestamp of the last cache update
  static Future<DateTime?> getLastCacheTimestamp() async {
    final prefs = await SharedPreferences.getInstance();
    final timestamp = prefs.getInt(_timestampKey);

    if (timestamp == null) {
      return null;
    }

    return DateTime.fromMillisecondsSinceEpoch(timestamp);
  }

  // Clear the persistent cache
  static Future<void> clearCache() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_cacheKey);
    await prefs.remove(_timestampKey);
    await prefs.remove(_fileMetadataKey);
  }

  // Check if cache is expired (older than specified duration)
  static Future<bool> isCacheExpired(Duration maxAge) async {
    final timestamp = await getLastCacheTimestamp();

    if (timestamp == null) {
      return true; // No cache exists
    }

    final now = DateTime.now();
    return now.difference(timestamp) > maxAge;
  }

  // Save file metadata for incremental scanning
  static Future<void> saveFileMetadata(
      Map<String, DateTime> fileModifiedTimes) async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJson = <String, int>{};

    fileModifiedTimes.forEach((path, modifiedTime) {
      metadataJson[path] = modifiedTime.millisecondsSinceEpoch;
    });

    await prefs.setString(_fileMetadataKey, jsonEncode(metadataJson));
  }

  // Load file metadata for incremental scanning
  static Future<Map<String, DateTime>?> loadFileMetadata() async {
    final prefs = await SharedPreferences.getInstance();
    final metadataJsonString = prefs.getString(_fileMetadataKey);

    if (metadataJsonString == null) {
      return null;
    }

    try {
      final metadataJson =
          jsonDecode(metadataJsonString) as Map<String, dynamic>;
      final metadata = <String, DateTime>{};

      metadataJson.forEach((path, modifiedTimeMs) {
        metadata[path] =
            DateTime.fromMillisecondsSinceEpoch(modifiedTimeMs as int);
      });

      return metadata;
    } catch (e) {
      return null;
    }
  }
}
