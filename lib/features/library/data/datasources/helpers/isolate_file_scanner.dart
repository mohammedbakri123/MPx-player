import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'package:path/path.dart' as path;
import '../../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

/// Isolate-based file scanner for non-blocking I/O operations
/// 
/// This moves expensive file system operations to a background isolate
/// to prevent UI jank during large directory scans.
class IsolateFileScanner {
  static final List<String> _videoExtensions = [
    '.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv',
    '.webm', '.m4v', '.3gp', '.ts', '.mts', '.m2ts'
  ];

  static const int _minFileSize = 100 * 1024; // 100KB
  static const int _maxDepth = 3;

  /// Scan directory in a background isolate
  /// 
  /// Returns a tuple of (videoCount, videoFiles)
  static Future<ScanResult> scanDirectory(IsolateScanRequest request) async {
    // Send work to isolate
    return await Isolate.run(() => _performScan(request));
  }

  /// Perform the actual scan in the isolate
  static ScanResult _performScan(IsolateScanRequest request) {
    final directory = Directory(request.directoryPath);
    final videos = <VideoFileData>[];
    final seenPaths = <String>{};
    var videoCount = 0;

    try {
      _scanDirectorySync(
        directory,
        videos,
        seenPaths,
        isIncremental: request.isIncremental,
        previousMetadata: request.previousMetadata,
        currentMetadata: request.currentMetadata,
        depth: 0,
      );
      videoCount = videos.length;
    } catch (e, stackTrace) {
      AppLogger.e('Error in isolate scan: $e', e, stackTrace);
    }

    return ScanResult(
      videoCount: videoCount,
      videos: videos,
      directoryPath: request.directoryPath,
    );
  }

  /// Synchronous directory scanning (runs in isolate)
  static void _scanDirectorySync(
    Directory directory,
    List<VideoFileData> videos,
    Set<String> seenPaths, {
    required bool isIncremental,
    Map<String, int>? previousMetadata,
    required Map<String, int> currentMetadata,
    required int depth,
  }) {
    if (depth > _maxDepth || _shouldSkipDirectory(directory.path)) {
      return;
    }

    List<FileSystemEntity> entities;
    try {
      entities = directory.listSync(recursive: false, followLinks: false);
    } catch (e) {
      return; // Directory not accessible
    }

    for (final entity in entities) {
      if (entity is File && _isVideoFile(entity.path)) {
        // Skip if already processed (duplicate prevention)
        if (seenPaths.contains(entity.path)) {
          continue;
        }

        _processFileSync(
          entity,
          videos,
          seenPaths,
          isIncremental: isIncremental,
          previousMetadata: previousMetadata,
          currentMetadata: currentMetadata,
        );
      } else if (entity is Directory && depth < _maxDepth) {
        _scanDirectorySync(
          entity,
          videos,
          seenPaths,
          isIncremental: isIncremental,
          previousMetadata: previousMetadata,
          currentMetadata: currentMetadata,
          depth: depth + 1,
        );
      }
    }
  }

  /// Process a single file (runs in isolate)
  static void _processFileSync(
    File file,
    List<VideoFileData> videos,
    Set<String> seenPaths, {
    required bool isIncremental,
    Map<String, int>? previousMetadata,
    required Map<String, int> currentMetadata,
  }) {
    try {
      final stat = file.statSync();
      
      // Skip files smaller than minimum size
      if (stat.size < _minFileSize) {
        return;
      }

      // Mark as processed to prevent duplicates
      seenPaths.add(file.path);

      // Incremental scan optimization
      if (isIncremental) {
        final previousTime = previousMetadata?[file.path];
        final currentTime = stat.modified.millisecondsSinceEpoch;

        // Skip if file hasn't changed since last scan
        if (previousTime != null && currentTime <= previousTime) {
          currentMetadata[file.path] = currentTime;
          return;
        }
        currentMetadata[file.path] = currentTime;
      }

      // Create video file data
      final videoData = VideoFileData(
        id: file.path.hashCode.toString(),
        path: file.path,
        title: path.basenameWithoutExtension(file.path),
        folderPath: path.dirname(file.path),
        folderName: path.basename(path.dirname(file.path)),
        size: stat.size,
        duration: 0,
        dateAddedMs: stat.modified.millisecondsSinceEpoch,
      );

      videos.add(videoData);
    } catch (e) {
      // File not accessible, skip
    }
  }

  static bool _isVideoFile(String filePath) {
    final ext = path.extension(filePath).toLowerCase();
    return _videoExtensions.contains(ext);
  }

  static bool _shouldSkipDirectory(String dirPath) {
    final name = path.basename(dirPath).toLowerCase();
    return name.startsWith('.') ||
        ['thumbnails', 'cache', 'temp', 'tmp', '.thumbnails', '.cache'].contains(name);
  }
}

/// Request object for isolate scanning
class IsolateScanRequest {
  final String directoryPath;
  final bool isIncremental;
  final Map<String, int>? previousMetadata;
  final Map<String, int> currentMetadata;

  IsolateScanRequest({
    required this.directoryPath,
    this.isIncremental = false,
    this.previousMetadata,
    required this.currentMetadata,
  });
}

/// Result object from isolate scanning
class ScanResult {
  final int videoCount;
  final List<VideoFileData> videos;
  final String directoryPath;

  ScanResult({
    required this.videoCount,
    required this.videos,
    required this.directoryPath,
  });
}

/// Lightweight video file data transfer object for isolate communication
/// 
/// This is used instead of VideoFile to avoid sending complex objects
/// across isolate boundaries.
class VideoFileData {
  final String id;
  final String path;
  final String title;
  final String folderPath;
  final String folderName;
  final int size;
  final int duration;
  final int dateAddedMs;

  VideoFileData({
    required this.id,
    required this.path,
    required this.title,
    required this.folderPath,
    required this.folderName,
    required this.size,
    required this.duration,
    required this.dateAddedMs,
  });

  /// Convert to VideoFile entity
  VideoFile toVideoFile() {
    return VideoFile(
      id: id,
      path: path,
      title: title,
      folderPath: folderPath,
      folderName: folderName,
      size: size,
      duration: duration,
      dateAdded: DateTime.fromMillisecondsSinceEpoch(dateAddedMs),
    );
  }

  /// Convert list of VideoFileData to list of VideoFile
  static List<VideoFile> toVideoFileList(List<VideoFileData> dataList) {
    return dataList.map((data) => data.toVideoFile()).toList();
  }
}
