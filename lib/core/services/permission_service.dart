import 'dart:io';
import 'package:permission_handler/permission_handler.dart';
import 'logger_service.dart';

class PermissionService {
  static int? _sdkInt;

  static Future<int> get _androidSdkInt async {
    if (_sdkInt != null) return _sdkInt!;
    if (!Platform.isAndroid) return 999;

    final deviceInfo = await _getAndroidDeviceInfo();
    _sdkInt = deviceInfo;
    AppLogger.i('Android SDK: $_sdkInt');
    return _sdkInt!;
  }

  static Future<int> _getAndroidDeviceInfo() async {
    try {
      final result = await Process.run('getprop', ['ro.build.version.sdk']);
      return int.parse(result.stdout.toString().trim());
    } catch (e) {
      return 999;
    }
  }

  static Future<bool> _isAndroid10OrLower() async {
    final sdk = await _androidSdkInt;
    return sdk <= 29;
  }

  static Future<bool> requestStoragePermissions() async {
    AppLogger.i('Requesting storage permissions...');

    if (await _isAndroid10OrLower()) {
      return await _requestStorageForOlderAndroid();
    } else {
      return await _requestMediaPermissionsForAndroid11Plus();
    }
  }

  static Future<bool> _requestStorageForOlderAndroid() async {
    final storageStatus = await Permission.storage.request();
    AppLogger.i('Storage permission (Android 10-): $storageStatus');
    return storageStatus.isGranted;
  }

  static Future<bool> _requestMediaPermissionsForAndroid11Plus() async {
    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    AppLogger.i('Photos: $photosStatus');
    AppLogger.i('Videos: $videosStatus');

    Map<Permission, PermissionStatus> statuses = await [
      Permission.photos,
      Permission.videos,
    ].request();

    final newPhotosStatus = statuses[Permission.photos]!;
    final newVideosStatus = statuses[Permission.videos]!;

    AppLogger.i('After request - Photos: $newPhotosStatus');
    AppLogger.i('After request - Videos: $newVideosStatus');

    if (newVideosStatus.isGranted) {
      AppLogger.i('Video permission granted!');
      return true;
    }

    if (newVideosStatus.isPermanentlyDenied) {
      AppLogger.w('Video permission permanently denied');
      return false;
    }

    final storageStatus = await Permission.storage.request();
    AppLogger.i('Storage permission: $storageStatus');

    return storageStatus.isGranted;
  }

  static Future<bool> checkStoragePermission() async {
    AppLogger.i('Checking storage permissions...');

    if (await _isAndroid10OrLower()) {
      return await _checkStorageForOlderAndroid();
    } else {
      return await _checkMediaPermissionsForAndroid11Plus();
    }
  }

  static Future<bool> _checkStorageForOlderAndroid() async {
    final storageStatus = await Permission.storage.status;
    AppLogger.i('Storage status (Android 10-): $storageStatus');
    return storageStatus.isGranted;
  }

  static Future<bool> _checkMediaPermissionsForAndroid11Plus() async {
    final photosStatus = await Permission.photos.status;
    final videosStatus = await Permission.videos.status;

    AppLogger.i('Photos status: $photosStatus');
    AppLogger.i('Videos status: $videosStatus');

    if (videosStatus.isGranted) {
      AppLogger.i('Video permission already granted');
      return true;
    }

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
