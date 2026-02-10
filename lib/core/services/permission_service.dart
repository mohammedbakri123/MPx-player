import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionService {
  static Future<bool> requestStoragePermissions() async {
    print('📝 Requesting storage permissions...');

    // First check current status
    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    print('📸 Photos: $photosStatus');
    print('🎬 Videos: $videosStatus');

    // Request both permissions
    Map<Permission, PermissionStatus> statuses = await [
      Permission.photos,
      Permission.videos,
    ].request();

    final newPhotosStatus = statuses[Permission.photos]!;
    final newVideosStatus = statuses[Permission.videos]!;

    print('📸 After request - Photos: $newPhotosStatus');
    print('🎬 After request - Videos: $newVideosStatus');

    // Check if video permission is granted
    if (newVideosStatus.isGranted) {
      print('✅ Video permission granted!');
      return true;
    }

    // If video permission is permanently denied, we can't request it again
    if (newVideosStatus.isPermanentlyDenied) {
      print('❌ Video permission permanently denied');
      return false;
    }

    // Try the broader storage permission as fallback
    final storageStatus = await Permission.storage.request();
    print('💾 Storage permission: $storageStatus');

    return storageStatus.isGranted;
  }

  static Future<bool> checkStoragePermission() async {
    print('🔍 Checking storage permissions...');

    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    print('📸 Photos status: $photosStatus');
    print('🎬 Videos status: $videosStatus');

    // For Android 13+, we specifically need video permission
    if (videosStatus.isGranted) {
      print('✅ Video permission already granted');
      return true;
    }

    // Check storage permission as fallback
    final storageStatus = await Permission.storage.status;
    print('💾 Storage status: $storageStatus');

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
    print('⚙️ Opening app settings...');
    await openAppSettings();
  }
}
