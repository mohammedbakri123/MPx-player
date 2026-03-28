import 'package:shared_preferences/shared_preferences.dart';

import '../domain/enums/quality_preference.dart';

class DownloaderSettingsService {
  static const String _autoUpdateKey = 'downloader_auto_update';
  static const String _defaultQualityKey = 'downloader_default_quality';
  static const String _downloadPathKey = 'downloader_download_path';
  static const String _cookiesPathKey = 'downloader_cookies_path';
  static const String _logsEnabledKey = 'downloader_logs_enabled';
  static const String _autoDownloadSharedLinksKey =
      'downloader_auto_download_shared_links';
  static const String _lastBinaryVersionKey = 'downloader_binary_version';
  static const String _lastUpdateCheckKey = 'downloader_last_update_check';
  static const String _latestBinaryVersionKey =
      'downloader_latest_binary_version';

  static late SharedPreferences _prefs;

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  static bool get autoUpdateEnabled => _prefs.getBool(_autoUpdateKey) ?? true;

  static QualityPreference get defaultQuality {
    final raw = _prefs.getString(_defaultQualityKey);
    for (final value in QualityPreference.values) {
      if (value.name == raw) {
        return value;
      }
    }
    return QualityPreference.auto;
  }

  static String? get cookiesPath => _prefs.getString(_cookiesPathKey);

  static String get downloadPath =>
      _prefs.getString(_downloadPathKey) ?? '/Movies/mpxReels';

  static bool get logsEnabled => _prefs.getBool(_logsEnabledKey) ?? false;

  static bool get autoDownloadSharedLinks =>
      _prefs.getBool(_autoDownloadSharedLinksKey) ?? true;

  static String? get binaryVersion => _prefs.getString(_lastBinaryVersionKey);

  static String? get latestBinaryVersion =>
      _prefs.getString(_latestBinaryVersionKey);

  static DateTime? get lastUpdateCheckAt {
    final raw = _prefs.getInt(_lastUpdateCheckKey);
    if (raw == null) {
      return null;
    }
    return DateTime.fromMillisecondsSinceEpoch(raw);
  }

  static Future<void> setAutoUpdateEnabled(bool value) async {
    await _prefs.setBool(_autoUpdateKey, value);
  }

  static Future<void> setDefaultQuality(QualityPreference value) async {
    await _prefs.setString(_defaultQualityKey, value.name);
  }

  static Future<void> setCookiesPath(String? value) async {
    if (value == null || value.isEmpty) {
      await _prefs.remove(_cookiesPathKey);
      return;
    }
    await _prefs.setString(_cookiesPathKey, value);
  }

  static Future<void> setDownloadPath(String value) async {
    final normalized = value.trim().isEmpty ? '/Movies/mpxReels' : value.trim();
    await _prefs.setString(_downloadPathKey, normalized);
  }

  static Future<void> setLogsEnabled(bool value) async {
    await _prefs.setBool(_logsEnabledKey, value);
  }

  static Future<void> setAutoDownloadSharedLinks(bool value) async {
    await _prefs.setBool(_autoDownloadSharedLinksKey, value);
  }

  static Future<void> setBinaryVersion(String? value) async {
    if (value == null || value.isEmpty) {
      await _prefs.remove(_lastBinaryVersionKey);
      return;
    }
    await _prefs.setString(_lastBinaryVersionKey, value);
  }

  static Future<void> setLatestBinaryVersion(String? value) async {
    if (value == null || value.isEmpty) {
      await _prefs.remove(_latestBinaryVersionKey);
      return;
    }
    await _prefs.setString(_latestBinaryVersionKey, value);
  }

  static Future<void> setLastUpdateCheckAt(DateTime? value) async {
    if (value == null) {
      await _prefs.remove(_lastUpdateCheckKey);
      return;
    }
    await _prefs.setInt(_lastUpdateCheckKey, value.millisecondsSinceEpoch);
  }
}
