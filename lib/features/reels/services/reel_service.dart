import 'dart:io';
import 'package:path/path.dart' as p;
import '../../../core/services/storage_path_service.dart';
import '../../../core/services/permission_service.dart';
import '../../library/domain/entities/video_file.dart';
import '../../library/domain/entities/file_item.dart'; // Import FileItem

class ReelService {
  static const String _reelsFolderName = 'mpxReels';
  static Directory? _reelsDirectory;

  // Initializes and returns the directory for mpxReels
  static Future<Directory> _getReelsDirectory() async {
    if (_reelsDirectory != null) {
      return _reelsDirectory!;
    }

    // Resolve correct user profile path (works for main, secure folder, work profiles)
    final appExternalDir = await StoragePathService.getAppExternalDirectory();
    // Extract user's base storage path (e.g /storage/emulated/[userId]/) by splitting off Android app directory
    final userBasePath = appExternalDir.path.split(RegExp(r'Android'))[0];
    final publicReelsPath = p.join(userBasePath, 'Movies', _reelsFolderName);

    // Check if we have permission to access public storage
    final hasManageStorage =
        await PermissionService.checkManageExternalStorage();

    Directory targetDir;
    if (hasManageStorage) {
      // Use public Movies/mpxReels path when permission is granted
      targetDir = Directory(publicReelsPath);
    } else {
      // Fallback to app-specific directory to avoid breaking the app
      targetDir = Directory(p.join(appExternalDir.path, _reelsFolderName));
    }

    if (!await targetDir.exists()) {
      try {
        await targetDir.create(recursive: true);
      } catch (e) {
        // If public path creation fails, fallback to app-specific directory
        if (targetDir.path == publicReelsPath) {
          targetDir = Directory(p.join(appExternalDir.path, _reelsFolderName));
          if (!await targetDir.exists()) {
            await targetDir.create(recursive: true);
          }
        } else {
          rethrow;
        }
      }
    }

    _reelsDirectory = targetDir;
    return targetDir;
  }

  // Returns the path of the Reels folder for user display
  static Future<String> getReelsFolderPath() async {
    final reelsDir = await _getReelsDirectory();
    return reelsDir.path;
  }

  // Imports video files from a source folder to the mpxReels directory
  static Future<void> importFolder(String sourcePath) async {
    final reelsDir = await _getReelsDirectory();
    final sourceDir = Directory(sourcePath);

    if (!await sourceDir.exists()) {
      throw Exception('Source directory does not exist: $sourcePath');
    }

    await for (final entity
        in sourceDir.list(recursive: false, followLinks: false)) {
      if (entity is File) {
        // Use FileItem.isVideoFileName to check if it's a video
        if (FileItem.isVideoFileName(p.basename(entity.path))) {
          final newPath = p.join(reelsDir.path, p.basename(entity.path));
          // Copy the file, if a file with the same name exists, it will be overwritten
          await entity.copy(newPath);
        }
      }
    }
  }

  // Imports a single video file to the mpxReels directory
  static Future<void> importVideoFile(String filePath) async {
    final reelsDir = await _getReelsDirectory();
    final file = File(filePath);

    if (!await file.exists()) {
      throw Exception('File does not exist: $filePath');
    }

    if (FileItem.isVideoFileName(p.basename(file.path))) {
      final newPath = p.join(reelsDir.path, p.basename(file.path));
      await file.copy(newPath);
    } else {
      throw Exception('Not a valid video file: $filePath');
    }
  }

  // Returns a list of VideoFile objects from ANY specified directory
  static Future<List<VideoFile>> getVideosFromAnyFolder(String path) async {
    final dir = Directory(path);
    final List<VideoFile> videos = [];

    if (!await dir.exists()) {
      return videos;
    }

    final folderPath = dir.path;
    final folderName = p.basename(dir.path);

    await for (final entity in dir.list(recursive: false, followLinks: false)) {
      if (entity is File) {
        // Use FileItem.isVideoFileName to check if it's a video
        if (FileItem.isVideoFileName(p.basename(entity.path))) {
          final stat = await entity.stat();
          videos.add(
            VideoFile(
              id: p.basename(entity.path), // Use filename as ID for simplicity
              path: entity.path,
              title: p.basenameWithoutExtension(entity.path),
              folderPath: folderPath, // Corrected parameter
              folderName: folderName, // Corrected parameter
              size: stat.size,
              duration:
                  0, // Default to 0, actual duration will be fetched by player
              dateAdded: stat.changed, // Using stat.changed as dateAdded
            ),
          );
        }
      }
    }
    return videos;
  }

  // Returns a list of VideoFile objects from the mpxReels directory
  static Future<List<VideoFile>> getReelsVideos() async {
    final reelsDir = await _getReelsDirectory();
    final List<VideoFile> videos = [];

    if (!await reelsDir.exists()) {
      return videos;
    }

    final folderPath = reelsDir.path;
    final folderName = p.basename(reelsDir.path);

    await for (final entity
        in reelsDir.list(recursive: false, followLinks: false)) {
      if (entity is File) {
        // Use FileItem.isVideoFileName to check if it's a video
        if (FileItem.isVideoFileName(p.basename(entity.path))) {
          final stat = await entity.stat();
          videos.add(
            VideoFile(
              id: p.basename(entity.path), // Use filename as ID for simplicity
              path: entity.path,
              title: p.basenameWithoutExtension(entity.path),
              folderPath: folderPath, // Corrected parameter
              folderName: folderName, // Corrected parameter
              size: stat.size,
              duration:
                  0, // Default to 0, actual duration will be fetched by player
              dateAdded: stat.changed, // Using stat.changed as dateAdded
            ),
          );
        }
      }
    }
    return videos;
  }
}
