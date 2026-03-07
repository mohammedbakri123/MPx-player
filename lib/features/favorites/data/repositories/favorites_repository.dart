import '../../../library/data/datasources/directory_browser.dart';
import '../../../library/domain/entities/video_file.dart';

class FavoritesRepository {
  static Future<List<VideoFile>> loadVideos() async {
    final browser = DirectoryBrowser();
    final storageDirs = await browser.getStorageDirectories();
    final allVideos = <VideoFile>[];

    for (final storageDir in storageDirs) {
      await _collectVideosRecursive(browser, storageDir, allVideos,
          depth: 0, maxDepth: 5);
    }

    allVideos.sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
    return allVideos;
  }

  static Future<void> _collectVideosRecursive(
    DirectoryBrowser browser,
    String path,
    List<VideoFile> videos, {
    required int depth,
    required int maxDepth,
  }) async {
    if (depth > maxDepth) return;

    try {
      final items = await browser.listDirectory(path);
      for (final item in items) {
        if (!item.isDirectory && item.isVideo) {
          videos.add(VideoFile.fromFileItem(item, path));
        } else if (item.isDirectory && depth < maxDepth) {
          await _collectVideosRecursive(browser, item.path, videos,
              depth: depth + 1, maxDepth: maxDepth);
        }
      }
    } catch (_) {
      // Skip inaccessible directories
    }
  }
}
