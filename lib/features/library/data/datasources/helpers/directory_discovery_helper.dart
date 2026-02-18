import 'dart:io';
import 'package:path_provider/path_provider.dart';
import '../../../../../../core/services/logger_service.dart';

/// Helper class for discovering video directories on the device
class DirectoryDiscoveryHelper {
  /// Get list of directories to scan for videos (deduplicated)
  static Future<List<Directory>> getDirectoriesToScan() async {
    final directories = <Directory>[];
    final uniquePaths = <String>{}; // Track unique canonical paths

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
            await _addDirectoryIfUnique(dir, directories, uniquePaths);
          }

          // Check for app-specific folders that commonly contain videos
          // Note: Prioritize scoped storage paths (Android/media/) over legacy paths
          final appDirs = [
            'Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video', // Scoped storage (Android 10+)
            'WhatsApp/Media/WhatsApp Video', // Legacy path
            'WhatsApp/Media/WhatsApp Animated Gifs',
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
            await _addDirectoryIfUnique(dir, directories, uniquePaths);
          }

          // Add Pictures and Music directories if they exist
          final picturesDir = Directory('$rootPath/Pictures');
          await _addDirectoryIfUnique(picturesDir, directories, uniquePaths);

          final musicDir = Directory('$rootPath/Music');
          await _addDirectoryIfUnique(musicDir, directories, uniquePaths);

          // Scan root for custom movie folders (like "Inception", "Movies", etc.)
          final rootDir = Directory(rootPath);
          if (await rootDir.exists()) {
            await _discoverVideoFolders(rootDir, directories, uniquePaths);
          }
        }
      }
    } catch (e) {
      AppLogger.e('Error getting directories: $e');
    }

    AppLogger.i('Found ${directories.length} unique directories to scan');
    return directories;
  }

  /// Discover all folders containing video files (for custom movie folders)
  static Future<void> _discoverVideoFolders(
    Directory rootDir,
    List<Directory> directories,
    Set<String> uniquePaths,
  ) async {
    final videoExtensions = [
      '.mp4',
      '.mkv',
      '.avi',
      '.mov',
      '.wmv',
      '.flv',
      '.webm',
      '.m4v',
      '.3gp'
    ];

    try {
      await for (final entity in rootDir.list(recursive: false)) {
        if (entity is Directory) {
          final dirName = entity.path.split('/').last.toLowerCase();

          // Skip system and cache directories
          if (dirName.startsWith('.') ||
              ['android', 'cache', 'tmp', 'temp', 'thumbnails']
                  .contains(dirName)) {
            continue;
          }

          // Check if this directory contains video files
          if (await _containsVideoFiles(entity, videoExtensions)) {
            await _addDirectoryIfUnique(entity, directories, uniquePaths);
          }
        }
      }
    } catch (e) {
      AppLogger.e('Error discovering video folders: $e');
    }
  }

  /// Check if a directory contains any video files (shallow check)
  static Future<bool> _containsVideoFiles(
      Directory dir, List<String> extensions) async {
    try {
      await for (final entity in dir.list(recursive: false)) {
        if (entity is File) {
          final ext = entity.path.toLowerCase();
          for (final videoExt in extensions) {
            if (ext.endsWith(videoExt)) {
              return true;
            }
          }
        }
      }
    } catch (e) {
      // Directory not accessible
    }
    return false;
  }

  /// Add directory if it's unique (not a symlink/duplicate of an existing path)
  static Future<void> _addDirectoryIfUnique(
    Directory dir,
    List<Directory> directories,
    Set<String> uniquePaths,
  ) async {
    if (!await dir.exists()) return;

    try {
      // Resolve to canonical path (follows symlinks)
      final canonicalPath = await dir.resolveSymbolicLinks();

      // Check if we already have this exact path
      if (uniquePaths.contains(canonicalPath)) {
        AppLogger.i(
            'Skipping duplicate directory: ${dir.path} (same as $canonicalPath)');
        return;
      }

      // Check if this path is a PARENT directory of an already added path
      // If we have DCIM/Camera, we don't need DCIM (but we DO need both DCIM and DCIM/Camera)
      for (final existingPath in uniquePaths) {
        if (existingPath.startsWith('$canonicalPath/')) {
          AppLogger.i(
              'Skipping parent directory: ${dir.path} (subdirectory $existingPath already added)');
          return;
        }
      }

      uniquePaths.add(canonicalPath);
      directories.add(dir);
      AppLogger.i('Added directory: ${dir.path} (canonical: $canonicalPath)');
    } catch (e) {
      // If we can't resolve symlinks, use the original path
      final path = dir.path;
      if (!uniquePaths.contains(path)) {
        uniquePaths.add(path);
        directories.add(dir);
        AppLogger.i('Added directory: $path');
      }
    }
  }
}
