import 'dart:io';
import '../../domain/entities/file_item.dart';

class DirectoryBrowser {
  static final DirectoryBrowser _instance = DirectoryBrowser._internal();
  factory DirectoryBrowser() => _instance;
  DirectoryBrowser._internal();

  final Map<String, List<FileItem>> _cache = {};
  final Map<String, int> _videoCountCache = {};

  int? getVideoCount(String path) => _videoCountCache[path];
  void setVideoCount(String path, int count) => _videoCountCache[path] = count;

  Future<List<String>> getStorageDirectories() async {
    final dirs = <String>[];

    if (Platform.isAndroid) {
      final internal = Directory('/storage/emulated/0');
      if (await internal.exists()) {
        dirs.add(internal.path);
      }

      final externalDir = Directory('/storage');
      if (await externalDir.exists()) {
        await for (final entity in externalDir.list()) {
          if (entity is Directory && !entity.path.contains('emulated')) {
            dirs.add(entity.path);
          }
        }
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
    // Only invalidate exact path to keep other navigation cache
    _cache.remove(path);
    _videoCountCache.remove(path);
  }

  String getRootPath() {
    if (Platform.isAndroid) {
      return '/storage/emulated/0';
    }
    return '/';
  }
}
