// import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'logger_service.dart';

class PermissionService {
  static Future<bool> requestStoragePermissions() async {
    AppLogger.i('Requesting storage permissions...');

    // First check current status
    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    AppLogger.i('Photos: $photosStatus');
    AppLogger.i('Videos: $videosStatus');

    // Request both permissions
    Map<Permission, PermissionStatus> statuses = await [
      Permission.photos,
      Permission.videos,
    ].request();

    final newPhotosStatus = statuses[Permission.photos]!;
    final newVideosStatus = statuses[Permission.videos]!;

    AppLogger.i('After request - Photos: $newPhotosStatus');
    AppLogger.i('After request - Videos: $newVideosStatus');

    // Check if video permission is granted
    if (newVideosStatus.isGranted) {
      AppLogger.i('Video permission granted!');
      return true;
    }

    // If video permission is permanently denied, we can't request it again
    if (newVideosStatus.isPermanentlyDenied) {
      AppLogger.w('Video permission permanently denied');
      return false;
    }

    // Try the broader storage permission as fallback
    final storageStatus = await Permission.storage.request();
    AppLogger.i('Storage permission: $storageStatus');

    return storageStatus.isGranted;
  }

  static Future<bool> checkStoragePermission() async {
    AppLogger.i('Checking storage permissions...');

    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    AppLogger.i('Photos status: $photosStatus');
    AppLogger.i('Videos status: $videosStatus');

    // For Android 13+, we specifically need video permission
    if (videosStatus.isGranted) {
      AppLogger.i('Video permission already granted');
      return true;
    }

    // Check storage permission as fallback
    final storageStatus = await Permission.storage.status;
    AppLogger.i('Storage status: $storageStatus');

    return storageStatus.isGranted;
  }

  static Future<Map<String, PermissionStatus>> getPermissionStatus() async {
    return {
      'photos': await Permission.photos.status,
      'videos': await Permission.videos.status,
      'storage': await Permission.storage.status,
    };
  }

  static Future<void> openSettings() async {
    AppLogger.i('Opening app settings...');
    await openAppSettings();
  }
}
