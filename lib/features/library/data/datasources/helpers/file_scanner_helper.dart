import 'dart:io';
import 'package:path/path.dart' as path;
import '../../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

/// Helper for scanning files and directories
class FileScannerHelper {
  static final List<String> _videoExtensions = [
    '.mp4',
    '.mkv',
    '.avi',
    '.mov',
    '.wmv',
    '.flv',
    '.webm',
    '.m4v',
    '.3gp',
    '.ts',
    '.mts',
    '.m2ts'
  ];

  static const int _minFileSize = 100 * 1024;
  static const int _maxDepth = 3;

  /// Scan directory with optional incremental mode and deduplication
  static Future<int> scanDirectory(
    Directory directory,
    List<VideoFile> videos, {
    Map<String, DateTime>? currentFileMetadata,
    Map<String, DateTime>? previousFileMetadata,
    Set<String>? processedPaths,
    Function(double progress, String status)? onProgress,
    int currentDirIndex = 0,
    int totalDirs = 0,
  }) async {
    final isIncremental = currentFileMetadata != null;
    final seenPaths = processedPaths ?? <String>{};
    var videoCount = 0;

    try {
      List<FileSystemEntity> entities;
      try {
        entities = await directory.list(recursive: false).toList();
      } catch (e) {
        return 0;
      }

      for (final entity in entities) {
        if (entity is File && _isVideoFile(entity.path)) {
          // Skip if already processed (duplicate)
          if (seenPaths.contains(entity.path)) continue;

          final count = await _processFile(
            entity,
            videos,
            seenPaths,
            isIncremental: isIncremental,
            currentFileMetadata: currentFileMetadata,
            previousFileMetadata: previousFileMetadata,
          );
          videoCount += count;
        } else if (entity is Directory) {
          await _scanSubdirectory(
            entity,
            videos,
            seenPaths,
            currentFileMetadata: currentFileMetadata,
            previousFileMetadata: previousFileMetadata,
            depth: 1,
          );
        }
      }

      _reportProgress(
          onProgress, currentDirIndex, totalDirs, directory, videoCount);
    } catch (e) {
      AppLogger.e('Error scanning ${directory.path}: $e');
    }

    return videoCount;
  }

  static bool _isVideoFile(String filePath) =>
      _videoExtensions.contains(path.extension(filePath).toLowerCase());

  static Future<int> _processFile(
    File file,
    List<VideoFile> videos,
    Set<String> seenPaths, {
    required bool isIncremental,
    Map<String, DateTime>? currentFileMetadata,
    Map<String, DateTime>? previousFileMetadata,
  }) async {
    try {
      final stat = await file.stat();
      if (stat.size < _minFileSize) return 0;

      // Mark as processed to prevent duplicates
      seenPaths.add(file.path);

      if (isIncremental) {
        final previousTime = previousFileMetadata?[file.path];
        final currentTime = stat.modified;

        if (previousTime != null && !currentTime.isAfter(previousTime)) {
          currentFileMetadata![file.path] = currentTime;
          return 0;
        }
        currentFileMetadata![file.path] = currentTime;
      }

      videos.add(_createVideoFile(file.path, stat));
      return 1;
    } catch (e) {
      return 0;
    }
  }

  static Future<void> _scanSubdirectory(
    Directory directory,
    List<VideoFile> videos,
    Set<String> seenPaths, {
    Map<String, DateTime>? currentFileMetadata,
    Map<String, DateTime>? previousFileMetadata,
    required int depth,
  }) async {
    if (depth > _maxDepth || _shouldSkipDirectory(directory.path)) return;

    final isIncremental = currentFileMetadata != null;

    try {
      await for (final entity in directory.list(recursive: false)) {
        if (entity is File && _isVideoFile(entity.path)) {
          // Skip if already processed
          if (seenPaths.contains(entity.path)) continue;

          await _processFile(
            entity,
            videos,
            seenPaths,
            isIncremental: isIncremental,
            currentFileMetadata: currentFileMetadata,
            previousFileMetadata: previousFileMetadata,
          );
        } else if (entity is Directory && depth < _maxDepth) {
          await _scanSubdirectory(
            entity,
            videos,
            seenPaths,
            currentFileMetadata: currentFileMetadata,
            previousFileMetadata: previousFileMetadata,
            depth: depth + 1,
          );
        }
      }
    } catch (e) {
      // Directory not accessible
    }
  }

  static bool _shouldSkipDirectory(String dirPath) {
    final name = path.basename(dirPath).toLowerCase();
    return name.startsWith('.') ||
        ['thumbnails', 'cache', 'temp', 'tmp'].contains(name);
  }

  static VideoFile _createVideoFile(String filePath, FileStat stat) {
    return VideoFile(
      id: filePath.hashCode.toString(),
      path: filePath,
      title: path.basenameWithoutExtension(filePath),
      folderPath: path.dirname(filePath),
      folderName: path.basename(path.dirname(filePath)),
      size: stat.size,
      duration: 0,
      dateAdded: stat.modified,
    );
  }

  static void _reportProgress(
    Function(double progress, String status)? onProgress,
    int currentDirIndex,
    int totalDirs,
    Directory directory,
    int videoCount,
  ) {
    if (onProgress != null && totalDirs > 0) {
      final progress = 0.1 + (currentDirIndex * 0.8 / totalDirs);
      onProgress(progress,
          'Scanning ${path.basename(directory.path)} ($videoCount videos)');
    }
  }
}
