import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import 'package:pool/pool.dart';
import 'package:photo_manager/photo_manager.dart';
import '../../../../../core/services/logger_service.dart';
import '../../../../../core/services/persistent_cache_service.dart';
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

  Future<List<VideoFolder>> scanForVideos(
      {bool forceRefresh = false,
      Function(double progress, String status)? onProgress}) async {
    // Check persistent cache first if not forcing refresh
    //check if it mandetory to refresh, if not it go to load folders from cache
    if (!forceRefresh) {
      //this check if cache has actully something
      final persistentCacheExpired =
          await PersistentCacheService.isCacheExpired(
              const Duration(hours: 1)); // Expire after 1 hour

      //if yes it just load it
      if (!persistentCacheExpired) {
        final cachedFolders = await PersistentCacheService.loadFromCache();
        if (cachedFolders != null && cachedFolders.isNotEmpty) {
          AppLogger.i(
              'Returning persistent cached results (${cachedFolders.length} folders)');

          // Also update in-memory cache
          _cachedFolders = cachedFolders;
          _lastScanTime = await PersistentCacheService.getLastCacheTimestamp();

          return cachedFolders;
        }
      }
    }

    // Return in-memory cached results if available and not forced to refresh
    if (!forceRefresh && _cachedFolders != null && _lastScanTime != null) {
      final timeSinceLastScan = DateTime.now().difference(_lastScanTime!);
      if (timeSinceLastScan < _minScanInterval && _cachedFolders!.isNotEmpty) {
        AppLogger.i(
            'Returning in-memory cached results (${_cachedFolders!.length} folders)');
        return _cachedFolders!;
      }
    }

    // Prevent multiple simultaneous scans
    if (_isScanning) {
      AppLogger.i('Scan already in progress, waiting...');
      // Wait for current scan to complete
      while (_isScanning) {
        await Future.delayed(const Duration(milliseconds: 100));
      }
      return _cachedFolders ?? [];
    }

    _isScanning = true;
    AppLogger.i('Starting video scan...');

    // Notify initial progress
    onProgress?.call(0.0, 'Initializing scan...');

    final stopwatch = Stopwatch()..start();
    final List<VideoFile> allVideos = [];

    try {
      // Try MediaStore first on Android for faster scanning
      if (defaultTargetPlatform == TargetPlatform.android && !forceRefresh) {
        onProgress?.call(0.05, 'Scanning with MediaStore...');
        final mediaStoreVideos = await _scanWithMediaStore(onProgress);

        if (mediaStoreVideos.isNotEmpty) {
          AppLogger.i(
              'MediaStore scan found ${mediaStoreVideos.length} videos');
          allVideos.addAll(mediaStoreVideos);

          // Process MediaStore results
          _cachedFolders = _groupVideosByFolder(allVideos);
          _lastScanTime = DateTime.now();

          if (allVideos.isNotEmpty) {
            await PersistentCacheService.saveToCache(_cachedFolders!);
          }

          onProgress?.call(
              1.0, 'Scan completed! Found ${allVideos.length} videos');
          _isScanning = false;
          stopwatch.stop();
          AppLogger.i(
              'MediaStore scan complete in ${stopwatch.elapsedMilliseconds}ms');
          return _cachedFolders!;
        } else {
          AppLogger.w(
              'MediaStore returned no videos, falling back to file system scan');
        }
      }

      // Get directories to scan
      final directoriesToScan = await _getDirectoriesToScan();
      AppLogger.i('Found ${directoriesToScan.length} directories to scan');

      onProgress?.call(
          0.1, 'Found ${directoriesToScan.length} directories to scan');

      if (directoriesToScan.isEmpty) {
        AppLogger.w('No directories found to scan');
        _isScanning = false;
        return [];
      }

      // Load previous file metadata for incremental scanning
      final previousFileMetadata =
          await PersistentCacheService.loadFileMetadata();
      final currentFileMetadata = <String, DateTime>{};

      // Scan each directory concurrently with limited concurrency for better performance
      final totalDirectories = directoriesToScan.length;

      // Create a pool to limit concurrent scans to 3 at a time
      final pool = Pool(3);
      final scanFutures = <Future<void>>[];
      int scanIndex = 0;

      for (int i = 0; i < directoriesToScan.length; i++) {
        final dir = directoriesToScan[i];
        if (await dir.exists()) {
          AppLogger.i('Scanning: ${dir.path}');
          final currentIndex = scanIndex++;

          // Schedule scan with limited concurrency using pool
          final scanFuture = pool.withResource(() async {
            // For forced refresh, always use full scan to ensure all videos are found
            // Otherwise, use incremental scanning if we have previous metadata
            if (forceRefresh) {
              await _scanDirectoryWithProgressFallback(
                  dir, allVideos, onProgress, currentIndex, totalDirectories);
            } else if (previousFileMetadata != null) {
              await _scanDirectoryIncremental(
                  dir,
                  allVideos,
                  currentFileMetadata,
                  previousFileMetadata,
                  onProgress,
                  currentIndex,
                  totalDirectories);
            } else {
              await _scanDirectoryWithProgressFallback(
                  dir, allVideos, onProgress, currentIndex, totalDirectories);
            }
          });

          scanFutures.add(scanFuture);
        }
      }

      // Wait for all scans to complete
      await Future.wait(scanFutures);
      await pool.close();

      onProgress?.call(0.95, 'Processing results...');

      stopwatch.stop();
      AppLogger.i(
          'Scan complete in ${stopwatch.elapsedMilliseconds}ms. Found ${allVideos.length} videos');

      // Process results
      _cachedFolders = _groupVideosByFolder(allVideos);
      _lastScanTime = DateTime.now();

      // Only save to persistent cache if we found videos
      // This prevents saving empty results that would cause "no videos found" to persist
      if (allVideos.isNotEmpty) {
        await PersistentCacheService.saveToCache(_cachedFolders!);
        await PersistentCacheService.saveFileMetadata(
            currentFileMetadata); // Save file metadata for next incremental scan
      }

      onProgress?.call(1.0, 'Scan completed! Found ${allVideos.length} videos');

      _isScanning = false;
      return _cachedFolders!;
    } catch (e, stackTrace) {
      AppLogger.e('Error scanning videos: $e', e, stackTrace);
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

        AppLogger.i('Root storage: $rootPath');

        // Check if we can access the root
        if (await Directory(rootPath).exists()) {
          // Common video directories - prioritize most likely locations
          final commonDirs = [
            'DCIM/Camera', // Most common camera location
            'DCIM', // General DCIM folder
            'Movies', // Standard movies location
            'Videos', // General videos location
            'Download', // Downloads often contain videos
            'Downloads', // Alternative download location
          ];

          for (final dirName in commonDirs) {
            final dir = Directory('$rootPath/$dirName');
            if (await dir.exists()) {
              AppLogger.i('Directory: $dirName');
              directories.add(dir);
            }
          }

          // Check for app-specific folders that commonly contain videos
          final appDirs = [
            'WhatsApp/Media/WhatsApp Video',
            'WhatsApp/Media/WhatsApp Animated Gifs',
            'Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video',
            'Telegram/Telegram Video',
            'Instagram',
            'TikTok',
            'ScreenRecorder',
            'ScreenRecord',
            'Recording',
            'Recordings',
          ];

          for (final appDir in appDirs) {
            final dir = Directory('$rootPath/$appDir');
            if (await dir.exists()) {
              AppLogger.i('App directory: $appDir');
              directories.add(dir);
            }
          }

          // Add Pictures and Music directories if they exist
          final picturesDir = Directory('$rootPath/Pictures');
          if (await picturesDir.exists()) {
            directories.add(picturesDir);
          }

          final musicDir = Directory('$rootPath/Music');
          if (await musicDir.exists()) {
            directories.add(musicDir);
          }

          // Add root directory to scan for any loose video files
          // Don't scan it if it has too many files to avoid performance issues
          final rootDir = Directory(rootPath);
          if (await rootDir.exists()) {
            directories.add(rootDir);
          }
        }
      }
    } catch (e) {
      AppLogger.e('Error getting directories: $e');
    }

    return directories;
  }

  /// Scan videos using Android MediaStore (much faster than file system traversal)
  Future<List<VideoFile>> _scanWithMediaStore(
      Function(double progress, String status)? onProgress) async {
    final videos = <VideoFile>[];

    try {
      // Request permission if needed
      final PermissionState permissionState =
          await PhotoManager.requestPermissionExtend();
      if (!permissionState.isAuth) {
        AppLogger.w('MediaStore permission denied');
        return videos;
      }

      AppLogger.i('Fetching videos from MediaStore...');

      // Fetch all video assets from MediaStore
      final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
        type: RequestType.video,
        filterOption: FilterOptionGroup(
          videoOption: const FilterOption(
            durationConstraint: DurationConstraint(
                min:
                    Duration.zero), // Include all videos regardless of duration
          ),
        ),
      );

      AppLogger.i('Found ${albums.length} video albums in MediaStore');

      if (albums.isEmpty) {
        return videos;
      }

      int totalVideos = 0;
      for (final album in albums) {
        totalVideos += await album.assetCountAsync;
      }

      AppLogger.i('Total videos in MediaStore: $totalVideos');

      if (totalVideos == 0) {
        return videos;
      }

      int processedCount = 0;

      // Process each album
      for (final album in albums) {
        try {
          final List<AssetEntity> assets = await album.getAssetListPaged(
            page: 0,
            size: await album.assetCountAsync,
          );

          for (final asset in assets) {
            try {
              // Get the file path
              final String? filePath =
                  await asset.originFile.then((file) => file?.path);

              if (filePath == null || filePath.isEmpty) {
                continue;
              }

              // Check if it's a valid video file
              final ext = path.extension(filePath).toLowerCase();
              if (!_videoExtensions.contains(ext)) {
                continue;
              }

              final folderPath = path.dirname(filePath);
              final folderName = path.basename(folderPath);

              final video = VideoFile(
                id: asset.id,
                path: filePath,
                title: asset.title ?? path.basenameWithoutExtension(filePath),
                folderPath: folderPath,
                folderName: folderName,
                size: (await asset.originFile
                        .then((file) => file?.lengthSync())) ??
                    0,
                duration:
                    asset.duration * 1000, // Convert seconds to milliseconds
                dateAdded: asset.createDateTime ?? DateTime.now(),
              );

              videos.add(video);
              processedCount++;

              // Update progress every 10 videos
              if (processedCount % 10 == 0 && totalVideos > 0) {
                final progress = 0.05 + (0.85 * (processedCount / totalVideos));
                onProgress?.call(progress,
                    'Loading videos from MediaStore ($processedCount/$totalVideos)...');
              }
            } catch (e) {
              AppLogger.e('Error processing MediaStore asset: $e');
              continue;
            }
          }
        } catch (e) {
          AppLogger.e('Error processing album ${album.name}: $e');
          continue;
        }
      }

      AppLogger.i(
          'MediaStore scan complete. Found ${videos.length} valid videos');
    } catch (e, stackTrace) {
      AppLogger.e('Error scanning MediaStore: $e', e, stackTrace);
    }

    return videos;
  }

  Future<void> _scanDirectory(
      Directory directory, List<VideoFile> videos) async {
    try {
      final List<FileSystemEntity> entities;
      try {
        entities = await directory.list(recursive: false).toList();
      } catch (e) {
        AppLogger.e('Cannot access ${directory.path}');
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
        AppLogger.i(
            'Found $videoCount videos in ${path.basename(directory.path)}');
      }
    } catch (e) {
      AppLogger.e('Error scanning ${directory.path}: $e');
    }
  }

  Future<void> _scanDirectoryIncremental(
      Directory directory,
      List<VideoFile> videos,
      Map<String, DateTime> currentFileMetadata,
      Map<String, DateTime>? previousFileMetadata,
      Function(double progress, String status)? onProgress,
      int currentDirIndex,
      int totalDirs) async {
    try {
      final List<FileSystemEntity> entities;
      try {
        entities = await directory.list(recursive: false).toList();
      } catch (e) {
        AppLogger.e('Cannot access ${directory.path}');
        return;
      }

      int videoCount = 0;

      for (final entity in entities) {
        try {
          if (entity is File) {
            final filePath = entity.path;
            final ext = path.extension(filePath).toLowerCase();

            if (_videoExtensions.contains(ext)) {
              // Get file stats
              FileStat fileStat;
              try {
                fileStat = await entity.stat();
              } catch (e) {
                continue; // Skip files we can't access
              }

              // Check if this file existed in the previous scan and if it's been modified
              final previousModifiedTime = previousFileMetadata?[filePath];
              final currentModifiedTime = fileStat.modified;

              // Only process the file if it's new or has been modified since last scan
              if (previousModifiedTime == null ||
                  currentModifiedTime.isAfter(previousModifiedTime)) {
                // Quick check without stat first
                final folderPath = entity.parent.path;
                final folderName = path.basename(folderPath);

                final video = VideoFile(
                  id: filePath.hashCode.toString(),
                  path: filePath,
                  title: path.basenameWithoutExtension(filePath),
                  folderPath: folderPath,
                  folderName: folderName,
                  size: fileStat.size,
                  duration: 0,
                  dateAdded: fileStat.modified,
                );

                videos.add(video);
                videoCount++;
              }

              // Always update current metadata
              currentFileMetadata[filePath] = currentModifiedTime;
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
            await _scanSubdirectoryIncremental(
                entity, videos, currentFileMetadata, previousFileMetadata,
                depth: 1);
          }
        } catch (e) {
          // Skip problematic files
        }
      }

      if (videoCount > 0) {
        AppLogger.i(
            'Found $videoCount videos in ${path.basename(directory.path)}');
      }

      // Update progress - distribute progress across directories
      if (totalDirs > 0) {
        final progressPerDir =
            0.8 / totalDirs; // Use 80% of progress for scanning
        final currentProgress = 0.1 +
            (currentDirIndex * progressPerDir); // Start after initialization
        onProgress?.call(currentProgress,
            'Scanning ${path.basename(directory.path)} ($videoCount videos found)');
      }
    } catch (e) {
      AppLogger.e('Error scanning ${directory.path}: $e');
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

  Future<void> _scanDirectoryWithProgressFallback(
      Directory directory,
      List<VideoFile> videos,
      Function(double progress, String status)? onProgress,
      int currentDirIndex,
      int totalDirs) async {
    try {
      final List<FileSystemEntity> entities;
      try {
        entities = await directory.list(recursive: false).toList();
      } catch (e) {
        AppLogger.e('Cannot access ${directory.path}');
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
        AppLogger.i(
            'Found $videoCount videos in ${path.basename(directory.path)}');
      }

      // Update progress - distribute progress across directories
      if (totalDirs > 0) {
        final progressPerDir =
            0.8 / totalDirs; // Use 80% of progress for scanning
        final currentProgress = 0.1 +
            (currentDirIndex * progressPerDir); // Start after initialization
        onProgress?.call(currentProgress,
            'Scanning ${path.basename(directory.path)} ($videoCount videos found)');
      }
    } catch (e) {
      AppLogger.e('Error scanning ${directory.path}: $e');
    }
  }

  Future<void> _scanSubdirectoryIncremental(
      Directory directory,
      List<VideoFile> videos,
      Map<String, DateTime> currentFileMetadata,
      Map<String, DateTime>? previousFileMetadata,
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

                // Check if this file existed in the previous scan and if it's been modified
                final previousModifiedTime = previousFileMetadata?[entity.path];
                final currentModifiedTime = stat.modified;

                // Only process the file if it's new or has been modified since last scan
                if (previousModifiedTime == null ||
                    currentModifiedTime.isAfter(previousModifiedTime)) {
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
                }

                // Always update current metadata
                currentFileMetadata[entity.path] = currentModifiedTime;
              } catch (e) {
                // Skip files we can't read
              }
            }
          } else if (entity is Directory && depth < 3) {
            final dirName = path.basename(entity.path).toLowerCase();
            if (!dirName.startsWith('.') &&
                !['thumbnails', 'cache', 'temp', 'tmp'].contains(dirName)) {
              await _scanSubdirectoryIncremental(
                  entity, videos, currentFileMetadata, previousFileMetadata,
                  depth: depth + 1);
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
    AppLogger.i('Cache cleared');
  }
}
