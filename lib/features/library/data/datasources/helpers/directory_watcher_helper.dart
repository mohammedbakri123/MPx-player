import 'dart:async';
import 'dart:io';
import 'package:path/path.dart' as path;
import 'package:watcher/watcher.dart' as pkg_watcher;
import '../../../../../../core/services/logger_service.dart';
import '../../../domain/entities/video_file.dart';

/// Watches directories for video file changes in real-time
class DirectoryWatcherHelper {
  final List<pkg_watcher.DirectoryWatcher> _watchers = [];
  final StreamController<VideoFile> _videoAddedController =
      StreamController<VideoFile>.broadcast();
  final StreamController<String> _videoRemovedController =
      StreamController<String>.broadcast();
  final StreamController<VideoFile> _videoModifiedController =
      StreamController<VideoFile>.broadcast();
  final List<StreamSubscription> _subscriptions = [];

  // Streams for UI to listen to
  Stream<VideoFile> get onVideoAdded => _videoAddedController.stream;
  Stream<String> get onVideoRemoved => _videoRemovedController.stream;
  Stream<VideoFile> get onVideoModified => _videoModifiedController.stream;

  bool get isWatching => _watchers.isNotEmpty;

  /// Start watching a list of directories
  Future<void> startWatching(List<Directory> directories) async {
    await stopWatching();

    AppLogger.i(
        'Starting directory watchers for ${directories.length} directories');

    for (final dir in directories) {
      if (await dir.exists()) {
        await _watchDirectory(dir);
      }
    }

    AppLogger.i('Directory watchers started: ${_watchers.length} active');
  }

  /// Watch a single directory
  Future<void> _watchDirectory(Directory directory) async {
    try {
      final watcher = pkg_watcher.DirectoryWatcher(
        directory.path,
        pollingDelay: const Duration(seconds: 30),
      );

      final sub = watcher.events.listen(
        (event) => _handleFileSystemEvent(event),
        onError: (e) => AppLogger.e('Watcher error for ${directory.path}: $e'),
      );

      _watchers.add(watcher);
      _subscriptions.add(sub);
      AppLogger.i('Watching: ${directory.path}');
    } catch (e) {
      AppLogger.e('Failed to watch ${directory.path}: $e');
    }
  }

  /// Handle file system events
  void _handleFileSystemEvent(pkg_watcher.WatchEvent event) {
    final filePath = event.path;
    final ext = path.extension(filePath).toLowerCase();

    if (!_isVideoFile(ext)) return;

    AppLogger.i('File system event: ${event.type} - $filePath');

    switch (event.type) {
      case pkg_watcher.ChangeType.ADD:
        _handleVideoAdded(filePath);
        break;
      case pkg_watcher.ChangeType.REMOVE:
        _handleVideoRemoved(filePath);
        break;
      case pkg_watcher.ChangeType.MODIFY:
        _handleVideoModified(filePath);
        break;
    }
  }

  /// Handle new video file
  Future<void> _handleVideoAdded(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) return;

      final stat = await file.stat();
      if (stat.size < 100 * 1024) return;

      final video = _createVideoFile(filePath, stat);
      _videoAddedController.add(video);
      AppLogger.i('Video added: ${video.title}');
    } catch (e) {
      AppLogger.e('Error handling video added: $e');
    }
  }

  /// Handle video removal
  void _handleVideoRemoved(String filePath) {
    _videoRemovedController.add(filePath);
    AppLogger.i('Video removed: ${path.basename(filePath)}');
  }

  /// Handle video modification
  Future<void> _handleVideoModified(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) return;

      final stat = await file.stat();
      final video = _createVideoFile(filePath, stat);
      _videoModifiedController.add(video);
      AppLogger.i('Video modified: ${video.title}');
    } catch (e) {
      AppLogger.e('Error handling video modified: $e');
    }
  }

  VideoFile _createVideoFile(String filePath, FileStat stat) {
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

  /// Stop all watchers
  Future<void> stopWatching() async {
    AppLogger.i('Stopping directory watchers');

    for (final sub in _subscriptions) {
      await sub.cancel();
    }
    _subscriptions.clear();
    _watchers.clear();

    AppLogger.i('Directory watchers stopped');
  }

  /// Dispose controllers
  void dispose() {
    stopWatching();
    _videoAddedController.close();
    _videoRemovedController.close();
    _videoModifiedController.close();
  }

  bool _isVideoFile(String ext) {
    return [
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
    ].contains(ext);
  }
}
