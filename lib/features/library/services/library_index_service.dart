import 'dart:io';

import '../domain/entities/file_item.dart';
import '../domain/entities/video_file.dart';

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

  final Map<String, LibraryIndexSnapshot> _snapshots = {};
  final Map<String, Future<LibraryIndexSnapshot>> _inFlight = {};

  LibraryIndexSnapshot? getSnapshot(String rootPath) => _snapshots[rootPath];

  int? getFolderVideoCount(String rootPath, String folderPath) {
    return _snapshots[rootPath]?.folderVideoCounts[folderPath];
  }

  Future<LibraryIndexSnapshot> ensureIndexed(
    String rootPath, {
    bool forceRefresh = false,
  }) {
    if (!forceRefresh && _snapshots.containsKey(rootPath)) {
      return Future.value(_snapshots[rootPath]!);
    }

    if (!forceRefresh && _inFlight.containsKey(rootPath)) {
      return _inFlight[rootPath]!;
    }

    final future = _buildIndex(rootPath);
    _inFlight[rootPath] = future;

    future.then((snapshot) {
      _snapshots[rootPath] = snapshot;
    }).whenComplete(() {
      _inFlight.remove(rootPath);
    });

    return future;
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

  void invalidate(String rootPath) {
    _snapshots.remove(rootPath);
    _inFlight.remove(rootPath);
  }

  Future<LibraryIndexSnapshot> _buildIndex(String rootPath) async {
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

    return LibraryIndexSnapshot(
      videos: videos,
      folderVideoCounts: folderVideoCounts,
      indexedAt: DateTime.now(),
    );
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
