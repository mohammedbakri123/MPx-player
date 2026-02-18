import 'package:path/path.dart' as path;
import '../../../domain/entities/video_file.dart';
import '../../../domain/entities/video_folder.dart';

/// Helper class for grouping videos by folder
class VideoGroupingHelper {
  /// Group videos by their folder path (with deduplication)
  static List<VideoFolder> groupByFolder(List<VideoFile> videos) {
    final Map<String, List<VideoFile>> folderMap = {};
    final seenPaths = <String>{}; // Track unique video paths

    for (final video in videos) {
      // Skip duplicate videos (same file path)
      if (seenPaths.contains(video.path)) {
        continue;
      }
      seenPaths.add(video.path);

      if (!folderMap.containsKey(video.folderPath)) {
        folderMap[video.folderPath] = [];
      }
      folderMap[video.folderPath]!.add(video);
    }

    return folderMap.entries.map((entry) {
      return VideoFolder(
        path: entry.key,
        name: path.basename(entry.key),
        videos: entry.value,
      );
    }).toList()
      ..sort((a, b) => b.videos.length.compareTo(a.videos.length));
  }
}
