import 'dart:io';
import 'package:path_provider/path_provider.dart';
import '../../../../../../core/services/logger_service.dart';

/// Helper class for discovering video directories on the device
class DirectoryDiscoveryHelper {
  /// Get list of directories to scan for videos
  static Future<List<Directory>> getDirectoriesToScan() async {
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
}
