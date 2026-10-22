import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:xdg_directories/xdg_directories.dart';

import '../../../../core/utils/logger.dart';
import '../../domain/entities/video_file.dart';

class FileSystemLibraryDatasource {
  static const String _tag = 'FileSystemLibraryDatasource';

  static const Set<String> _videoExtensions = <String>{
    '.mp4', '.mkv', '.webm', '.avi', '.mov', '.m4v', '.ts', '.flv', '.wmv',
  };

  Future<List<String>> scanRoots() async {
    final Directory? videosDir = getUserDirectory('VIDEOS');
    final String videosPath = videosDir?.path ?? '';
    if (videosPath.isNotEmpty) {
      return <String>[videosPath];
    }
    final String home = Platform.environment['HOME'] ?? '';
    if (home.isEmpty) {
      return const <String>[];
    }
    return <String>[p.join(home, 'Videos')];
  }

  Future<List<VideoFile>> fetchVideos() async {
    final List<String> roots = await scanRoots();
    final List<VideoFile> videos = <VideoFile>[];

    for (final String root in roots) {
      final Directory rootDir = Directory(root);
      if (!rootDir.existsSync()) {
        AppLogger.i(_tag, 'Scan root missing, skipped: $root');
        continue;
      }
      await for (final FileSystemEntity entity
          in rootDir.list(recursive: true, followLinks: false)) {
        if (entity is! File) continue;
        if (!_videoExtensions
            .contains(p.extension(entity.path).toLowerCase())) {
          continue;
        }
        final VideoFile? video = _toVideoFile(entity);
        if (video != null) {
          videos.add(video);
        }
      }
    }
    return videos;
  }

  VideoFile? _toVideoFile(File file) {
    try {
      final FileStat stat = file.statSync();
      return VideoFile(
        id: file.path,
        path: file.path,
        name: p.basename(file.path),
        durationMs: 0,
        sizeBytes: stat.size,
        dateModifiedMs: stat.modified.millisecondsSinceEpoch,
        folderPath: p.dirname(file.path),
      );
    } catch (e, s) {
      AppLogger.e(_tag, 'Failed to stat file ${file.path}', e, s);
      return null;
    }
  }
}
