import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
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
    final appDocDir = await getApplicationDocumentsDirectory();
    final reelsDir = Directory(p.join(appDocDir.path, _reelsFolderName));
    if (!await reelsDir.exists()) {
      await reelsDir.create(recursive: true);
    }
    _reelsDirectory = reelsDir;
    return reelsDir;
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
