import 'dart:io';
import '../../../../core/services/storage_path_service.dart';
import '../../domain/entities/file_item.dart';

class DirectoryBrowser {
  static final DirectoryBrowser _instance = DirectoryBrowser._internal();
  factory DirectoryBrowser() => _instance;
  DirectoryBrowser._internal();

  final Map<String, List<FileItem>> _cache = {};
  final Map<String, int> _videoCountCache = {};

  int? getVideoCount(String path) => _videoCountCache[path];
  void setVideoCount(String path, int count) => _videoCountCache[path] = count;

  /// Returns available storage directories.
  /// On Android 10+ (API 29+), direct access to /storage/emulated/0 is blocked
  /// by Scoped Storage. This method first tries the legacy path, then falls back
  /// to app-accessible directories.
  /// In Samsung Secure Folder, paths resolve to the isolated profile automatically.
  Future<List<String>> getStorageDirectories() async {
    final dirs = <String>[];

    if (Platform.isAndroid) {
      // Try the legacy public storage path first.
      // This works on Android 9 and below, or if MANAGE_EXTERNAL_STORAGE is granted.
      try {
        final internal = Directory('/storage/emulated/0');
        if (await internal.exists()) {
          // Verify we can actually list contents (exists() alone may pass)
          await for (final _ in internal.list()) {
            break; // Just check if we can list at all
          }
          dirs.add(internal.path);
        }
      } catch (e) {
        // Scoped Storage blocks direct access - fall back to app directories
      }

      // If legacy path is inaccessible, use app-specific directories.
      // These work on all Android versions and profiles (including Secure Folder).
      if (dirs.isEmpty) {
        try {
          final appVolumes = await StoragePathService.getAccessibleStorageVolumes();
          for (final dir in appVolumes) {
            if (await dir.exists()) {
              dirs.add(dir.path);
            }
          }
        } catch (e) {
          // Fall through to final fallback
        }
      }

      // Last resort: try to find SD cards and other mounted storage
      if (dirs.isEmpty) {
        try {
          final externalDir = Directory('/storage');
          if (await externalDir.exists()) {
            await for (final entity in externalDir.list()) {
              if (entity is Directory && !entity.path.contains('emulated')) {
                dirs.add(entity.path);
              }
            }
          }
        } catch (_) {}
      }
    } else if (Platform.isIOS) {
      final documentsDir = Directory('/var/mobile/Containers/Data/Application');
      if (await documentsDir.exists()) {
        dirs.add(documentsDir.path);
      }
    }

    return dirs;
  }

  Future<List<FileItem>> listDirectory(String path,
      {bool forceRefresh = false}) async {
    if (!forceRefresh && _cache.containsKey(path)) {
      return _cache[path]!;
    }

    try {
      final dir = Directory(path);
      if (!await dir.exists()) {
        return [];
      }

      final items = <FileItem>[];
      // Faster listing by minimizing stat calls during the initial loop
      await for (final entity in dir.list(followLinks: false)) {
        final name = entity.path.split('/').last;
        if (name.startsWith('.')) continue;

        if (entity is File) {
          // Fast extension check before doing expensive stat
          if (!FileItem.isVideoFileName(name)) continue;

          final stat = await entity.stat();
          items.add(FileItem(
            path: entity.path,
            name: name,
            isDirectory: false,
            size: stat.size,
            modified: stat.modified,
          ));
        } else if (entity is Directory) {
          // For folders, we still need modified date for sorting,
          // but we can skip size (it's always 0 anyway)
          final stat = await entity.stat();
          items.add(FileItem(
            path: entity.path,
            name: name,
            isDirectory: true,
            size: 0,
            modified: stat.modified,
          ));
        }
      }

      items.sort((a, b) {
        if (a.isDirectory && !b.isDirectory) return -1;
        if (!a.isDirectory && b.isDirectory) return 1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });

      _cache[path] = items;
      return items;
    } catch (e) {
      return [];
    }
  }

  void clearCache() {
    _cache.clear();
    _videoCountCache.clear();
  }

  void invalidatePath(String path) {
    _cache.remove(path);
    _videoCountCache.remove(path);
  }

  /// Invalidate a path and all its descendant paths in the cache.
  void invalidatePathTree(String path) {
    _cache.removeWhere((key, _) => key == path || key.startsWith('$path/'));
    _videoCountCache
        .removeWhere((key, _) => key == path || key.startsWith('$path/'));
  }

  /// Returns the root path for file browsing.
  /// Note: On Android 10+ with Scoped Storage, this path may not be directly
  /// accessible. Use [getStorageDirectories] to get accessible paths.
  String getRootPath() {
    if (Platform.isAndroid) {
      // This is the legacy path. On modern Android, it may not be accessible.
      // Callers should handle PathAccessException and fall back to app directories.
      return '/storage/emulated/0';
    }
    return '/';
  }
}
