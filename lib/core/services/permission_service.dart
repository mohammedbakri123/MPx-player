import 'dart:io';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'logger_service.dart';

class PermissionService {
  /// Checks if the app has storage access permission.
  /// On Android 13+ (API 33+), checks READ_MEDIA_* permissions.
  /// On Android 10-12 (API 29-32), checks READ_EXTERNAL_STORAGE.
  /// On older versions, checks WRITE_EXTERNAL_STORAGE.
  static Future<bool> checkStoragePermission() async {
    AppLogger.i('Checking storage permissions...');

    if (Platform.isAndroid) {
      final sdkInt = await _getSdkInt();

      // Android 13+ (API 33+): Use granular media permissions
      if (sdkInt >= 33) {
        try {
          final videos = await Permission.videos.status;
          if (videos.isGranted) {
            AppLogger.i('Videos permission granted');
            return true;
          }
          final photos = await Permission.photos.status;
          if (photos.isGranted) {
            AppLogger.i('Photos permission granted');
            return true;
          }
        } catch (e) {
          AppLogger.w('Error checking media permissions: $e');
        }
      }

      // Android 10-12 (API 29-32): Use READ_EXTERNAL_STORAGE
      if (sdkInt >= 29) {
        final storage = await Permission.storage.status;
        AppLogger.i('Storage permission: $storage');
        return storage.isGranted;
      }

      // Older Android: Use WRITE_EXTERNAL_STORAGE
      final storage = await Permission.storage.status;
      AppLogger.i('Legacy storage permission: $storage');
      return storage.isGranted;
    }

    // Non-Android platforms
    return true;
  }

  /// Requests storage permissions appropriate for the Android version.
  static Future<bool> requestStoragePermissions() async {
    AppLogger.i('Requesting storage permissions...');

    if (Platform.isAndroid) {
      final sdkInt = await _getSdkInt();

      // Android 13+ (API 33+): Request granular media permissions
      if (sdkInt >= 33) {
        try {
          final photos = await Permission.photos.request();
          final videos = await Permission.videos.request();
          final audio = await Permission.audio.request();

          AppLogger.i('Photos: $photos, Videos: $videos, Audio: $audio');

          if (photos.isGranted || videos.isGranted || audio.isGranted) {
            return true;
          }
        } catch (e) {
          AppLogger.w('Error requesting media permissions: $e');
        }
      }

      // Fallback to general storage permission
      final storage = await Permission.storage.request();
      AppLogger.i('Storage permission: $storage');
      return storage.isGranted;
    }

    return true;
  }

  /// Checks if the app has MANAGE_EXTERNAL_STORAGE (All Files Access) permission.
  /// This is required for direct file system access on Android 11+ (API 30+).
  /// Note: Google Play requires justification for this permission.
  static Future<bool> checkManageExternalStorage() async {
    if (!Platform.isAndroid) return true;
    final status = await Permission.manageExternalStorage.status;
    return status.isGranted;
  }

  /// Requests MANAGE_EXTERNAL_STORAGE permission.
  /// On Android 11+, this opens the system settings page for All Files Access.
  static Future<bool> requestManageExternalStorage() async {
    if (!Platform.isAndroid) return true;

    final sdkInt = await _getSdkInt();
    if (sdkInt < 30) {
      // Not needed on Android 10 and below
      return true;
    }

    final status = await Permission.manageExternalStorage.request();
    AppLogger.i('Manage external storage permission: $status');
    return status.isGranted;
  }

  /// Returns detailed permission status for diagnostics.
  static Future<Map<String, PermissionStatus>> getPermissionStatus() async {
    final result = <String, PermissionStatus>{};

    if (Platform.isAndroid) {
      try {
        result['photos'] = await Permission.photos.status;
        result['videos'] = await Permission.videos.status;
        result['audio'] = await Permission.audio.status;
      } catch (_) {
        result['photos'] = PermissionStatus.denied;
        result['videos'] = PermissionStatus.denied;
        result['audio'] = PermissionStatus.denied;
      }

      result['storage'] = await Permission.storage.status;
      result['manageExternalStorage'] =
          await Permission.manageExternalStorage.status;
    }

    return result;
  }

  /// Opens the app settings page.
  static Future<void> openSettings() async {
    AppLogger.i('Opening app settings...');
    await openAppSettings();
  }

  /// Gets the Android SDK version.
  static Future<int> _getSdkInt() async {
    try {
      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;
      return androidInfo.version.sdkInt;
    } catch (e) {
      AppLogger.w('Failed to get SDK version: $e');
      return 33; // Assume modern Android if detection fails
    }
  }
}
