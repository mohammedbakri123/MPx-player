import 'package:permission_handler/permission_handler.dart';
import 'logger_service.dart';

class PermissionService {
  static Future<bool> requestStoragePermissions() async {
    AppLogger.i('Requesting storage permissions...');

    try {
      final photos = await Permission.photos.request();
      final videos = await Permission.videos.request();
      final audio = await Permission.audio.request();

      AppLogger.i('Photos: $photos, Videos: $videos, Audio: $audio');

      if (photos.isGranted || videos.isGranted || audio.isGranted) {
        return true;
      }
    } catch (_) {}

    final storage = await Permission.storage.request();
    AppLogger.i('Storage: $storage');
    return storage.isGranted;
  }

  static Future<bool> checkStoragePermission() async {
    AppLogger.i('Checking storage permissions...');

    try {
      final videos = await Permission.videos.status;
      if (videos.isGranted) {
        AppLogger.i('Videos granted');
        return true;
      }
      final photos = await Permission.photos.status;
      if (photos.isGranted) {
        AppLogger.i('Photos granted');
        return true;
      }
    } catch (_) {}

    final storage = await Permission.storage.status;
    AppLogger.i('Storage: $storage');
    return storage.isGranted;
  }

  static Future<Map<String, PermissionStatus>> getPermissionStatus() async {
    final result = <String, PermissionStatus>{};

    try {
      result['photos'] = await Permission.photos.status;
      result['videos'] = await Permission.videos.status;
    } catch (_) {
      result['photos'] = PermissionStatus.denied;
      result['videos'] = PermissionStatus.denied;
    }

    result['storage'] = await Permission.storage.status;
    return result;
  }

  static Future<void> openSettings() async {
    AppLogger.i('Opening app settings...');
    await openAppSettings();
  }
}
