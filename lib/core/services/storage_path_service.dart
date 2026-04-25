import 'dart:io';
import 'package:path_provider/path_provider.dart';

/// Service to handle storage path resolution across different Android profiles
/// including Samsung Secure Folder and work profiles.
///
/// Problem: Hardcoding '/storage/emulated/0' fails on:
/// - Android 10+ (API 29+) due to Scoped Storage restrictions
/// - Samsung Secure Folder (isolated user profile)
/// - Work profiles (different user ID)
///
/// Solution: Use platform APIs to get the correct paths instead of hardcoding.
class StoragePathService {
  /// Returns the app-specific external files directory.
  /// This works reliably across all Android versions and profiles
  /// without requiring any storage permissions.
  static Future<Directory> getAppExternalDirectory() async {
    if (Platform.isAndroid) {
      // getExternalStorageDirectory() returns /storage/emulated/[userId]/Android/data/<package>/files
      // This automatically resolves to the correct path for the current user profile
      // (main profile, Secure Folder, work profile, etc.)
      final dir = await getExternalStorageDirectory();
      if (dir != null) return dir;
    }
    return await getApplicationDocumentsDirectory();
  }

  /// Returns the app-specific cache directory.
  static Future<Directory> getAppCacheDirectory() async {
    if (Platform.isAndroid) {
      final dir = await getTemporaryDirectory();
      return dir;
    }
    final appDir = await getApplicationDocumentsDirectory();
    final cacheDir = Directory('${appDir.path}/cache');
    if (!await cacheDir.exists()) {
      await cacheDir.create(recursive: true);
    }
    return cacheDir;
  }

  /// Returns a list of available storage volumes for the current user profile.
  /// On Android, this returns app-accessible directories only.
  static Future<List<Directory>> getAccessibleStorageVolumes() async {
    final dirs = <Directory>[];

    if (Platform.isAndroid) {
      // Primary external storage (always available)
      final primary = await getExternalStorageDirectory();
      if (primary != null) {
        dirs.add(primary);
      }

      // Secondary external storage (SD cards, USB, etc.)
      try {
        final externalDirs = await getExternalStorageDirectories();
        if (externalDirs != null) {
          for (final dir in externalDirs) {
            if (!dirs.any((d) => d.path == dir.path)) {
              dirs.add(dir);
            }
          }
        }
      } catch (_) {
        // getExternalStorageDirectories may not be available on all platforms
      }
    } else {
      final appDir = await getApplicationDocumentsDirectory();
      dirs.add(appDir);
    }

    return dirs;
  }

  /// Checks if a path is accessible using direct file I/O.
  /// This is useful for detecting Scoped Storage restrictions.
  static Future<bool> isPathDirectlyAccessible(String path) async {
    try {
      final dir = Directory(path);
      await dir.exists();
      // Try to list contents to verify full access
      await dir.list().first;
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Gets a safe path for reels storage.
  /// Uses app-specific directory to avoid all permission issues.
  static Future<Directory> getReelsDirectory() async {
    final baseDir = await getAppExternalDirectory();
    final reelsDir = Directory('${baseDir.path}/mpxReels');
    if (!await reelsDir.exists()) {
      await reelsDir.create(recursive: true);
    }
    return reelsDir;
  }

  /// Gets the legacy public path (for backward compatibility only).
  /// WARNING: This path may not be accessible on Android 10+.
  static String getLegacyPublicPath() {
    if (Platform.isAndroid) {
      return '/storage/emulated/0';
    }
    return '/';
  }
}
