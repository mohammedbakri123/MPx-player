import 'dart:io';

import '../../../core/database/app_database.dart';
import '../../../core/services/logger_service.dart';
import '../domain/entities/file_item.dart';
import '../domain/entities/video_file.dart';
import '../domain/entities/video_folder.dart';

enum LibrarySearchSort { relevance, recent, name, size }

class LibraryIndexSnapshot {
  final List<VideoFile> videos;
  final Map<String, int> folderVideoCounts;
  final DateTime indexedAt;

  const LibraryIndexSnapshot({
    required this.videos,
    required this.folderVideoCounts,
    required this.indexedAt,
  });
}

class LibraryIndexService {
  static final LibraryIndexService _instance = LibraryIndexService._internal();

  factory LibraryIndexService() => _instance;

  LibraryIndexService._internal();

  final AppDatabase _db = AppDatabase();
  final Map<String, LibraryIndexSnapshot> _snapshots = {};
  final Map<String, Future<LibraryIndexSnapshot>> _inFlight = {};

  LibraryIndexSnapshot? getSnapshot(String rootPath) => _snapshots[rootPath];

  Future<bool> hasPersistedIndex(String rootPath) async {
    final db = await _db.database;
    final metadata = await _db.getLibraryIndexMetadata(db, rootPath);
    return metadata != null;
  }

  int? getFolderVideoCount(String rootPath, String folderPath) {
    return _snapshots[rootPath]?.folderVideoCounts[folderPath];
  }

  Future<LibraryIndexSnapshot> ensureIndexed(
    String rootPath, {
    bool forceRefresh = false,
  }) async {
    if (!forceRefresh && _snapshots.containsKey(rootPath)) {
      return _snapshots[rootPath]!;
    }

    if (!forceRefresh && _inFlight.containsKey(rootPath)) {
      return _inFlight[rootPath]!;
    }

    final future = _loadOrBuildIndex(rootPath, forceRefresh);
    _inFlight[rootPath] = future;

    try {
      final snapshot = await future;
      _snapshots[rootPath] = snapshot;
      return snapshot;
    } finally {
      _inFlight.remove(rootPath);
    }
  }

  Future<LibraryIndexSnapshot> _loadOrBuildIndex(
      String rootPath, bool forceRefresh) async {
    if (!forceRefresh) {
      final db = await _db.database;
      final metadata = await _db.getLibraryIndexMetadata(db, rootPath);

      if (metadata != null) {
        AppLogger.i('Loading library index from database for $rootPath');
        final videos = await _db.getAllVideos();

        // Filter videos that belong to this root path (if multiple roots supported)
        final rootVideos =
            videos.where((v) => v.path.startsWith(rootPath)).toList();

        if (rootVideos.isNotEmpty) {
          final folderVideoCounts = <String, int>{};
          for (final video in rootVideos) {
            _incrementFolderCounts(
                folderVideoCounts, rootPath, video.folderPath);
          }

          return LibraryIndexSnapshot(
            videos: rootVideos,
            folderVideoCounts: folderVideoCounts,
            indexedAt:
                DateTime.fromMillisecondsSinceEpoch(metadata['indexed_at']),
          );
        }
      }
    }

    return _buildIndex(rootPath);
  }

  Future<List<VideoFile>> search(
    String rootPath,
    String query, {
    LibrarySearchSort sortBy = LibrarySearchSort.relevance,
  }) async {
    final normalizedQuery = query.trim().toLowerCase();
    if (normalizedQuery.isEmpty) {
      return const [];
    }

    final snapshot = await ensureIndexed(rootPath);
    final terms = normalizedQuery
        .split(RegExp(r'\s+'))
        .where((term) => term.isNotEmpty)
        .toList(growable: false);

    final matches = <_ScoredVideo>[];

    for (final video in snapshot.videos) {
      final score = _scoreVideo(video, normalizedQuery, terms);
      if (score > 0) {
        matches.add(_ScoredVideo(video: video, score: score));
      }
    }

    matches.sort((a, b) => _compareMatches(a, b, sortBy));
    return matches.map((match) => match.video).toList(growable: false);
  }

  Future<void> invalidate(String rootPath) async {
    _snapshots.remove(rootPath);
    _inFlight.remove(rootPath);
    try {
      final db = await _db.database;
      await _db.deleteLibraryIndexMetadata(db, rootPath);
      // We might also want to delete all videos if this is the only root,
      // but for now let's just delete metadata to trigger re-index.
    } catch (e) {
      AppLogger.e('Failed to invalidate library index in database: $e');
    }
  }

  Future<void> refreshInBackground(String rootPath) {
    if (_inFlight.containsKey(rootPath)) {
      return _inFlight[rootPath]!.then((_) {});
    }

    return ensureIndexed(rootPath, forceRefresh: true).then((_) {});
  }

  Future<LibraryIndexSnapshot> _buildIndex(String rootPath) async {
    AppLogger.i('Building library index from scratch for $rootPath');
    final root = Directory(rootPath);
    if (!await root.exists()) {
      return LibraryIndexSnapshot(
        videos: [],
        folderVideoCounts: {},
        indexedAt: DateTime.fromMillisecondsSinceEpoch(0),
      );
    }

    final videos = <VideoFile>[];
    final folderVideoCounts = <String, int>{};
    final directories = <String>[rootPath];
    var processedDirectories = 0;

    while (directories.isNotEmpty) {
      final currentPath = directories.removeLast();
      final currentDirectory = Directory(currentPath);

      try {
        await for (final entity in currentDirectory.list(followLinks: false)) {
          if (entity is Directory) {
            final name = entity.path.split('/').last;
            if (_shouldSkipDirectory(entity.path, name)) {
              continue;
            }
            directories.add(entity.path);
            continue;
          }

          if (entity is! File) {
            continue;
          }

          final name = entity.path.split('/').last;
          if (!FileItem.isVideoFileName(name)) {
            continue;
          }

          final stat = await entity.stat();
          final folderPath = _parentPath(entity.path);
          final item = FileItem(
            path: entity.path,
            name: name,
            isDirectory: false,
            size: stat.size,
            modified: stat.modified,
          );

          videos.add(VideoFile.fromFileItem(item, folderPath));
          _incrementFolderCounts(folderVideoCounts, rootPath, folderPath);
        }
      } catch (_) {
        // Ignore folders the app cannot access.
      }

      processedDirectories++;
      if (processedDirectories % 25 == 0) {
        await Future<void>.delayed(Duration.zero);
      }
    }

    final now = DateTime.now();
    final snapshot = LibraryIndexSnapshot(
      videos: videos,
      folderVideoCounts: folderVideoCounts,
      indexedAt: now,
    );

    // Persist to database
    try {
      final db = await _db.database;
      await _db.insertVideos(videos);

      // Group videos by folder for correct folder persistence
      final videosByFolder = <String, List<VideoFile>>{};
      for (final video in videos) {
        videosByFolder.putIfAbsent(video.folderPath, () => []).add(video);
      }

      final folders = videosByFolder.entries
          .map((entry) => VideoFolder(
                path: entry.key,
                name: entry.key.split('/').last,
                videos: entry.value,
              ))
          .toList();

      await _db.insertFolders(folders);

      await _db.saveLibraryIndexMetadata(db, rootPath, now);
      AppLogger.i(
          'Saved ${videos.length} videos and index metadata to database');
    } catch (e) {
      AppLogger.e('Failed to persist library index: $e');
    }

    return snapshot;
  }

  bool _shouldSkipDirectory(String path, String name) {
    if (name.startsWith('.')) {
      return true;
    }

    if (path.endsWith('/Android') || path.contains('/Android/')) {
      return true;
    }

    return false;
  }

  void _incrementFolderCounts(
    Map<String, int> folderVideoCounts,
    String rootPath,
    String folderPath,
  ) {
    var currentPath = folderPath;

    while (currentPath.startsWith(rootPath)) {
      folderVideoCounts[currentPath] =
          (folderVideoCounts[currentPath] ?? 0) + 1;

      if (currentPath == rootPath) {
        break;
      }

      final parentPath = _parentPath(currentPath);
      if (parentPath == currentPath) {
        break;
      }
      currentPath = parentPath;
    }
  }

  int _scoreVideo(VideoFile video, String query, List<String> terms) {
    final title = video.title.toLowerCase();
    final folderName = video.folderName.toLowerCase();
    final path = video.path.toLowerCase();

    if (!title.contains(query) &&
        !folderName.contains(query) &&
        !terms.every((term) => path.contains(term))) {
      return 0;
    }

    var score = 0;

    if (title == query) score += 1200;
    if (title.startsWith(query)) score += 800;
    if (title.contains(query)) score += 500;
    if (folderName.contains(query)) score += 180;

    for (final term in terms) {
      if (title.startsWith(term)) {
        score += 180;
      } else if (title.contains(term)) {
        score += 120;
      }

      if (folderName.contains(term)) {
        score += 60;
      }
    }

    final age = DateTime.now().difference(video.dateAdded);
    if (age.inDays <= 7) {
      score += 90;
    } else if (age.inDays <= 30) {
      score += 45;
    } else if (age.inDays <= 90) {
      score += 20;
    }

    return score;
  }

  int _compareMatches(
    _ScoredVideo a,
    _ScoredVideo b,
    LibrarySearchSort sortBy,
  ) {
    switch (sortBy) {
      case LibrarySearchSort.relevance:
        final scoreCompare = b.score.compareTo(a.score);
        if (scoreCompare != 0) return scoreCompare;
        final dateCompare = b.video.dateAdded.compareTo(a.video.dateAdded);
        if (dateCompare != 0) return dateCompare;
        return a.video.title
            .toLowerCase()
            .compareTo(b.video.title.toLowerCase());
      case LibrarySearchSort.recent:
        final dateCompare = b.video.dateAdded.compareTo(a.video.dateAdded);
        if (dateCompare != 0) return dateCompare;
        return a.video.title
            .toLowerCase()
            .compareTo(b.video.title.toLowerCase());
      case LibrarySearchSort.name:
        return a.video.title
            .toLowerCase()
            .compareTo(b.video.title.toLowerCase());
      case LibrarySearchSort.size:
        final sizeCompare = b.video.size.compareTo(a.video.size);
        if (sizeCompare != 0) return sizeCompare;
        return a.video.title
            .toLowerCase()
            .compareTo(b.video.title.toLowerCase());
    }
  }

  String _parentPath(String path) {
    final lastSeparator = path.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return '/';
    }
    return path.substring(0, lastSeparator);
  }
}

class _ScoredVideo {
  final VideoFile video;
  final int score;

  const _ScoredVideo({required this.video, required this.score});
}
