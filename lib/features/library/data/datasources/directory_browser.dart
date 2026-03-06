import 'dart:io';
import '../../domain/entities/file_item.dart';

class DirectoryBrowser {
  static final DirectoryBrowser _instance = DirectoryBrowser._internal();
  factory DirectoryBrowser() => _instance;
  DirectoryBrowser._internal();

  final Map<String, List<FileItem>> _cache = {};

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
      await for (final entity in dir.list()) {
        final stat = await entity.stat();

        if (entity is File) {
          items.add(FileItem(
            path: entity.path,
            name: entity.path.split('/').last,
            isDirectory: false,
            size: stat.size,
            modified: stat.modified,
          ));
        } else if (entity is Directory) {
          final name = entity.path.split('/').last;
          if (!name.startsWith('.')) {
            items.add(FileItem(
              path: entity.path,
              name: name,
              isDirectory: true,
              size: 0,
              modified: stat.modified,
            ));
          }
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

  List<FileItem> filterVideos(List<FileItem> items) {
    return items.where((item) => item.isVideo).toList();
  }

  void clearCache() {
    _cache.clear();
  }

  void invalidatePath(String path) {
    _cache.remove(path);
  }

  String getParentPath(String path) {
    final lastSep = path.lastIndexOf('/');
    if (lastSep <= 0) return '/';
    return path.substring(0, lastSep);
  }

  bool isRoot(String path) {
    if (Platform.isAndroid) {
      return path == '/storage/emulated/0' || path == '/';
    }
    return path == '/';
  }

  String getRootPath() {
    if (Platform.isAndroid) {
      return '/storage/emulated/0';
    }
    return '/';
  }
}
