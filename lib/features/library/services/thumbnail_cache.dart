import 'dart:collection';
import 'dart:io';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';
import '../../../core/services/logger_service.dart';

class ThumbnailMemoryCache {
  static final ThumbnailMemoryCache _instance =
      ThumbnailMemoryCache._internal();
  factory ThumbnailMemoryCache() => _instance;
  ThumbnailMemoryCache._internal();

  static const int _maxEntries = 200;
  static const int _maxMemoryBytes = 50 * 1024 * 1024;

  final LinkedHashMap<String, _CacheEntry> _cache =
      LinkedHashMap<String, _CacheEntry>();
  int _currentMemoryUsage = 0;

  int _hits = 0;
  int _misses = 0;

  Uint8List? get(String videoPath) {
    final entry = _cache.remove(videoPath);
    if (entry != null) {
      _cache[videoPath] = entry;
      _hits++;
      return entry.data;
    }
    _misses++;
    return null;
  }

  void put(String videoPath, Uint8List data) {
    final existingEntry = _cache.remove(videoPath);
    if (existingEntry != null) {
      _currentMemoryUsage -= existingEntry.data.length;
    }

    while (_cache.length >= _maxEntries ||
        _currentMemoryUsage + data.length > _maxMemoryBytes) {
      if (_cache.isEmpty) break;
      final oldestKey = _cache.keys.first;
      final oldestEntry = _cache.remove(oldestKey)!;
      _currentMemoryUsage -= oldestEntry.data.length;
    }

    _cache[videoPath] = _CacheEntry(data, DateTime.now());
    _currentMemoryUsage += data.length;
  }

  void remove(String videoPath) {
    final entry = _cache.remove(videoPath);
    if (entry != null) {
      _currentMemoryUsage -= entry.data.length;
    }
  }

  void clear() {
    _cache.clear();
    _currentMemoryUsage = 0;
    _hits = 0;
    _misses = 0;
    AppLogger.i('Thumbnail memory cache cleared');
  }

  Map<String, dynamic> get stats => {
        'entries': _cache.length,
        'memory_mb': (_currentMemoryUsage / (1024 * 1024)).toStringAsFixed(2),
        'hits': _hits,
        'misses': _misses,
        'hit_rate': _hits + _misses > 0
            ? '${((_hits / (_hits + _misses)) * 100).toStringAsFixed(1)}%'
            : '0%',
      };
}

class _CacheEntry {
  final Uint8List data;
  final DateTime cachedAt;
  _CacheEntry(this.data, this.cachedAt);
}

class ThumbnailDiskCache {
  static final ThumbnailDiskCache _instance = ThumbnailDiskCache._internal();
  factory ThumbnailDiskCache() => _instance;
  ThumbnailDiskCache._internal();

  static const int _maxCacheSizeBytes = 200 * 1024 * 1024;
  static const int _maxCacheAgeMs = 30 * 24 * 60 * 60 * 1000; // 30 days in ms

  String? _cacheDir;

  Future<String> get _directory async {
    if (_cacheDir != null) return _cacheDir!;
    final appDir = await getApplicationDocumentsDirectory();
    final dir = Directory('${appDir.path}/thumbnails');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    _cacheDir = dir.path;
    return _cacheDir!;
  }

  Future<String?> get(String videoPath) async {
    try {
      final dir = await _directory;
      final fileName = '${videoPath.hashCode.abs()}.jpg';
      final file = File('$dir/$fileName');

      if (!await file.exists()) return null;

      final videoFile = File(videoPath);
      if (await videoFile.exists()) {
        final videoModified = await videoFile.lastModified();
        final thumbnailModified = await file.lastModified();

        if (videoModified.isAfter(thumbnailModified)) {
          await file.delete();
          AppLogger.d('Thumbnail invalidated (video modified): $videoPath');
          return null;
        }
      }

      if (await _isExpired(file)) {
        await file.delete();
        AppLogger.d('Thumbnail expired: $videoPath');
        return null;
      }

      return file.path;
    } catch (e) {
      return null;
    }
  }

  Future<bool> _isExpired(File file) async {
    try {
      final stat = await file.stat();
      final age = DateTime.now().millisecondsSinceEpoch -
          stat.modified.millisecondsSinceEpoch;
      return age > _maxCacheAgeMs;
    } catch (e) {
      return true;
    }
  }

  Future<int> getCacheSize() async {
    try {
      final dir = await _directory;
      final directory = Directory(dir);
      if (!await directory.exists()) return 0;

      int size = 0;
      await for (final entity in directory.list()) {
        if (entity is File) {
          size += await entity.length();
        }
      }
      return size;
    } catch (e) {
      return 0;
    }
  }

  Future<int> getCacheFileCount() async {
    try {
      final dir = await _directory;
      final directory = Directory(dir);
      if (!await directory.exists()) return 0;

      int count = 0;
      await for (final entity in directory.list()) {
        if (entity is File) {
          count++;
        }
      }
      return count;
    } catch (e) {
      return 0;
    }
  }

  Future<void> cleanup() async {
    try {
      final currentSize = await getCacheSize();
      if (currentSize <= _maxCacheSizeBytes) {
        AppLogger.d(
            'Thumbnail cache within limits: ${(currentSize / 1024 / 1024).toStringAsFixed(1)}MB');
        return;
      }

      final dir = await _directory;
      final directory = Directory(dir);
      if (!await directory.exists()) return;

      final files = <MapEntry<File, DateTime>>[];
      await for (final entity in directory.list()) {
        if (entity is File) {
          final stat = await entity.stat();
          files.add(MapEntry(entity, stat.modified));
        }
      }

      files.sort((a, b) => a.value.compareTo(b.value));

      int deletedSize = 0;
      final targetDeletion =
          currentSize - _maxCacheSizeBytes + (20 * 1024 * 1024);

      for (final entry in files) {
        if (deletedSize >= targetDeletion) break;

        try {
          final file = entry.key;
          final size = await file.length();
          await file.delete();
          deletedSize += size;
        } catch (e) {
          continue;
        }
      }

      AppLogger.i(
          'Thumbnail cache cleanup: deleted ${(deletedSize / 1024 / 1024).toStringAsFixed(1)}MB, '
          'remaining: ${((currentSize - deletedSize) / 1024 / 1024).toStringAsFixed(1)}MB');
    } catch (e) {
      AppLogger.e('Error cleaning thumbnail cache: $e');
    }
  }

  Future<void> clear() async {
    try {
      final dir = await _directory;
      final directory = Directory(dir);
      if (await directory.exists()) {
        await for (final entity in directory.list()) {
          if (entity is File) {
            await entity.delete();
          }
        }
      }
      AppLogger.i('Thumbnail disk cache cleared');
    } catch (e) {
      AppLogger.e('Error clearing thumbnail disk cache: $e');
    }
  }

  Map<String, dynamic> getStatsSync(int size, int count) {
    return {
      'size_mb': (size / (1024 * 1024)).toStringAsFixed(2),
      'file_count': count,
      'max_size_mb': (_maxCacheSizeBytes / (1024 * 1024)).toStringAsFixed(0),
    };
  }
}

class ThumbnailCache {
  static final ThumbnailCache _instance = ThumbnailCache._internal();
  factory ThumbnailCache() => _instance;
  ThumbnailCache._internal();

  final memoryCache = ThumbnailMemoryCache();
  final diskCache = ThumbnailDiskCache();

  Future<String?> get(String videoPath) async {
    final memoryData = memoryCache.get(videoPath);
    if (memoryData != null) {
      final dir = await diskCache._directory;
      return '$dir/${videoPath.hashCode.abs()}.jpg';
    }

    return await diskCache.get(videoPath);
  }

  Future<void> put(String videoPath, Uint8List data) async {
    memoryCache.put(videoPath, data);
  }

  Future<void> invalidate(String videoPath) async {
    memoryCache.remove(videoPath);

    try {
      final dir = await diskCache._directory;
      final file = File('$dir/${videoPath.hashCode.abs()}.jpg');
      if (await file.exists()) {
        await file.delete();
      }
    } catch (e) {
      // Ignore
    }
  }

  Future<void> cleanup() async {
    await diskCache.cleanup();
  }

  Future<void> clear() async {
    memoryCache.clear();
    await diskCache.clear();
  }

  Future<Map<String, dynamic>> getStats() async {
    final diskSize = await diskCache.getCacheSize();
    final diskCount = await diskCache.getCacheFileCount();

    return {
      'memory': memoryCache.stats,
      'disk': diskCache.getStatsSync(diskSize, diskCount),
    };
  }
}
