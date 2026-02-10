import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';

class VideoScanner {
  // Singleton pattern
  static final VideoScanner _instance = VideoScanner._internal();
  factory VideoScanner() => _instance;
  VideoScanner._internal();

  // Cache for scan results
  List<VideoFolder>? _cachedFolders;
  DateTime? _lastScanTime;
  bool _isScanning = false;

  // Minimum time between scans (5 seconds)
  static const _minScanInterval = Duration(seconds: 5);

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

  Future<List<VideoFolder>> scanForVideos({bool forceRefresh = false}) async {
    // Return cached results if available and not forced to refresh
    if (!forceRefresh && _cachedFolders != null && _lastScanTime != null) {
      final timeSinceLastScan = DateTime.now().difference(_lastScanTime!);
      if (timeSinceLastScan < _minScanInterval) {
        print(
            '📦 Returning cached results (${_cachedFolders!.length} folders)');
        return _cachedFolders!;
      }
    }

    // Prevent multiple simultaneous scans
    if (_isScanning) {
      print('⏳ Scan already in progress, waiting...');
      // Wait for current scan to complete
      while (_isScanning) {
        await Future.delayed(const Duration(milliseconds: 100));
      }
      return _cachedFolders ?? [];
    }

    _isScanning = true;
    print('🔍 Starting video scan...');

    final stopwatch = Stopwatch()..start();
    final List<VideoFile> allVideos = [];

    try {
      // Get directories to scan
      final directoriesToScan = await _getDirectoriesToScan();
      print('📁 Found ${directoriesToScan.length} directories to scan');

      if (directoriesToScan.isEmpty) {
        print('⚠️ No directories found to scan');
        _isScanning = false;
        return [];
      }

      // Scan each directory
      for (final dir in directoriesToScan) {
        if (await dir.exists()) {
          print('🔎 Scanning: ${dir.path}');
          await _scanDirectory(dir, allVideos);
        }
      }

      stopwatch.stop();
      print(
          '✅ Scan complete in ${stopwatch.elapsedMilliseconds}ms. Found ${allVideos.length} videos');

      // Cache results
      _cachedFolders = _groupVideosByFolder(allVideos);
      _lastScanTime = DateTime.now();

      _isScanning = false;
      return _cachedFolders!;
    } catch (e, stackTrace) {
      print('❌ Error scanning videos: $e');
      print(stackTrace);
      _isScanning = false;
      return _cachedFolders ?? [];
    }
  }

  Future<List<Directory>> _getDirectoriesToScan() async {
    final directories = <Directory>[];

    try {
      // External storage (primary)
      final externalDir = await getExternalStorageDirectory();
      if (externalDir != null) {
        // Navigate to root storage
        String rootPath = externalDir.path;
        while (rootPath.contains('/Android')) {
          final index = rootPath.lastIndexOf('/Android');
          if (index > 0) {
            rootPath = rootPath.substring(0, index);
          } else {
            break;
          }
        }

        print('📱 Root storage: $rootPath');

        // Check if we can access the root
        if (await Directory(rootPath).exists()) {
          // Common video directories
          final commonDirs = [
            'DCIM/Camera',
            'DCIM',
            'Movies',
            'Videos',
            'Download',
            'Downloads',
            'Pictures',
            'Music',
            'Documents',
          ];

          for (final dirName in commonDirs) {
            final dir = Directory('$rootPath/$dirName');
            if (await dir.exists()) {
              print('  ✅ Directory: $dirName');
              directories.add(dir);
            }
          }

          // Check for app-specific folders
          final appDirs = [
            'WhatsApp/Media/WhatsApp Video',
            'WhatsApp/Media/WhatsApp Animated Gifs',
            'Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video',
            'Telegram/Telegram Video',
            'Telegram/Telegram Documents',
            'Instagram',
            'Snapchat',
            'Facebook',
            'Messenger',
            'TikTok',
            'Xiaomi',
            'MIUI',
            'ScreenRecorder',
            'ScreenRecord',
            'Recording',
            'Recordings',
          ];

          for (final appDir in appDirs) {
            final dir = Directory('$rootPath/$appDir');
            if (await dir.exists()) {
              print('  ✅ App directory: $appDir');
              directories.add(dir);
            }
          }

          // Also scan the root storage for any loose video files
          directories.add(Directory(rootPath));
        }
      }
    } catch (e) {
      print('❌ Error getting directories: $e');
    }

    return directories;
  }

  Future<void> _scanDirectory(
      Directory directory, List<VideoFile> videos) async {
    try {
      final List<FileSystemEntity> entities;
      try {
        entities = await directory.list(recursive: false).toList();
      } catch (e) {
        print('  ❌ Cannot access ${directory.path}');
        return;
      }

      int videoCount = 0;

      for (final entity in entities) {
        try {
          if (entity is File) {
            final filePath = entity.path;
            final ext = path.extension(filePath).toLowerCase();

            if (_videoExtensions.contains(ext)) {
              // Quick check without stat first
              final folderPath = entity.parent.path;
              final folderName = path.basename(folderPath);

              // Try to get file size, but don't fail if we can't
              int fileSize = 0;
              DateTime modifiedDate = DateTime.now();
              try {
                final stat = await entity.stat();
                fileSize = stat.size;
                modifiedDate = stat.modified;
              } catch (e) {
                // If we can't stat, use defaults
              }

              final video = VideoFile(
                id: filePath.hashCode.toString(),
                path: filePath,
                title: path.basenameWithoutExtension(filePath),
                folderPath: folderPath,
                folderName: folderName,
                size: fileSize,
                duration: 0,
                dateAdded: modifiedDate,
              );

              videos.add(video);
              videoCount++;
            }
          } else if (entity is Directory) {
            // Scan subdirectories but be selective
            final dirName = path.basename(entity.path);
            final lowerName = dirName.toLowerCase();

            // Skip system folders
            if (lowerName.startsWith('.') ||
                ['thumbnails', 'cache', 'temp', 'tmp', 'trash']
                    .contains(lowerName)) {
              continue;
            }

            // Limit recursion depth
            await _scanSubdirectory(entity, videos, depth: 1);
          }
        } catch (e) {
          // Skip problematic files
        }
      }

      if (videoCount > 0) {
        print(
            '  ✅ Found $videoCount videos in ${path.basename(directory.path)}');
      }
    } catch (e) {
      print('  ❌ Error scanning ${directory.path}: $e');
    }
  }

  Future<void> _scanSubdirectory(Directory directory, List<VideoFile> videos,
      {required int depth}) async {
    if (depth > 3) return; // Limit depth to 3 levels

    try {
      await for (final entity in directory.list(recursive: false)) {
        try {
          if (entity is File) {
            final ext = path.extension(entity.path).toLowerCase();
            if (_videoExtensions.contains(ext)) {
              try {
                final stat = await entity.stat();
                final folderPath = entity.parent.path;
                final folderName = path.basename(folderPath);

                final video = VideoFile(
                  id: entity.path.hashCode.toString(),
                  path: entity.path,
                  title: path.basenameWithoutExtension(entity.path),
                  folderPath: folderPath,
                  folderName: folderName,
                  size: stat.size,
                  duration: 0,
                  dateAdded: stat.modified,
                );

                videos.add(video);
              } catch (e) {
                // Skip files we can't read
              }
            }
          } else if (entity is Directory && depth < 3) {
            final dirName = path.basename(entity.path).toLowerCase();
            if (!dirName.startsWith('.') &&
                !['thumbnails', 'cache', 'temp', 'tmp'].contains(dirName)) {
              await _scanSubdirectory(entity, videos, depth: depth + 1);
            }
          }
        } catch (e) {
          // Skip
        }
      }
    } catch (e) {
      // Directory not accessible
    }
  }

  List<VideoFolder> _groupVideosByFolder(List<VideoFile> videos) {
    final Map<String, List<VideoFile>> folderMap = {};

    for (final video in videos) {
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

  Future<List<VideoFile>> getVideosInFolder(String folderPath) async {
    // Use cached results if available
    if (_cachedFolders != null) {
      for (final folder in _cachedFolders!) {
        if (folder.path == folderPath) {
          return folder.videos;
        }
      }
    }

    // Otherwise scan the specific folder
    final videos = <VideoFile>[];
    final dir = Directory(folderPath);

    if (await dir.exists()) {
      await _scanDirectory(dir, videos);
    }

    return videos..sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
  }

  // Demo data for testing
  static List<VideoFolder> getDemoData() {
    final demoFolder = VideoFolder(
      path: '/storage/emulated/0/Demo',
      name: 'Demo Videos',
      videos: [
        VideoFile(
          id: 'demo1',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
          title: 'Big Buck Bunny',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 158000000,
          duration: 596000,
          dateAdded: DateTime.now().subtract(const Duration(days: 1)),
        ),
        VideoFile(
          id: 'demo2',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
          title: 'Elephants Dream',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 105700000,
          duration: 653000,
          dateAdded: DateTime.now().subtract(const Duration(days: 2)),
        ),
        VideoFile(
          id: 'demo3',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
          title: 'Tears of Steel',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 154000000,
          duration: 734000,
          dateAdded: DateTime.now().subtract(const Duration(days: 3)),
        ),
      ],
    );

    return [demoFolder];
  }

  // Clear cache (useful for pull-to-refresh)
  void clearCache() {
    _cachedFolders = null;
    _lastScanTime = null;
    print('🗑️ Cache cleared');
  }
}
