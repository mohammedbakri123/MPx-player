import '../../features/downloader/services/downloader_settings_service.dart';
import 'downloader_platform_service.dart';

class BinaryStatus {
  final bool ytDlpAvailable;
  final bool ffmpegAvailable;
  final String? ytDlpPath;
  final String? ffmpegPath;
  final String? version;
  final String? latestVersion;
  final bool updateAvailable;
  final bool updated;
  final bool requiresRestart;
  final String? message;

  const BinaryStatus({
    required this.ytDlpAvailable,
    required this.ffmpegAvailable,
    this.ytDlpPath,
    this.ffmpegPath,
    this.version,
    this.latestVersion,
    this.updateAvailable = false,
    this.updated = false,
    this.requiresRestart = false,
    this.message,
  });
}

class BinaryManager {
  BinaryManager._();

  static final BinaryManager instance = BinaryManager._();

  final DownloaderPlatformService _platform =
      DownloaderPlatformService.instance;

  BinaryStatus _status = const BinaryStatus(
    ytDlpAvailable: false,
    ffmpegAvailable: false,
  );

  BinaryStatus get status => _status;

  Future<void> ensureBinariesAvailable() async {
    final ensured = await _platform.ensureBinariesAvailable(
      ytDlpPath: null,
      ffmpegPath: null,
      version: DownloaderSettingsService.binaryVersion,
    );

    _status = BinaryStatus(
      ytDlpAvailable: ensured['ytDlpAvailable'] as bool? ?? false,
      ffmpegAvailable: ensured['ffmpegAvailable'] as bool? ?? false,
      ytDlpPath: ensured['ytDlpPath'] as String?,
      ffmpegPath: ensured['ffmpegPath'] as String?,
      version: ensured['version'] as String? ??
          DownloaderSettingsService.binaryVersion,
      latestVersion: ensured['latestVersion'] as String? ??
          DownloaderSettingsService.latestBinaryVersion,
      updateAvailable: ensured['updateAvailable'] as bool? ?? false,
      updated: ensured['updated'] as bool? ?? false,
      requiresRestart: ensured['requiresRestart'] as bool? ?? false,
      message: ensured['message'] as String?,
    );
    if (_status.version != null) {
      await DownloaderSettingsService.setBinaryVersion(_status.version);
    }
    await DownloaderSettingsService.setLatestBinaryVersion(
        _status.latestVersion);
  }

  Future<BinaryStatus> checkForUpdates({bool installIfAvailable = true}) async {
    await DownloaderSettingsService.setLastUpdateCheckAt(DateTime.now());
    final result = await _platform.checkForUpdates(
      installIfAvailable: installIfAvailable,
    );
    _status = BinaryStatus(
      ytDlpAvailable:
          result['ytDlpAvailable'] as bool? ?? _status.ytDlpAvailable,
      ffmpegAvailable:
          result['ffmpegAvailable'] as bool? ?? _status.ffmpegAvailable,
      ytDlpPath: result['ytDlpPath'] as String? ?? _status.ytDlpPath,
      ffmpegPath: result['ffmpegPath'] as String? ?? _status.ffmpegPath,
      version: result['version'] as String? ?? _status.version,
      latestVersion:
          result['latestVersion'] as String? ?? _status.latestVersion,
      updateAvailable:
          result['updateAvailable'] as bool? ?? _status.updateAvailable,
      updated: result['updated'] as bool? ?? false,
      requiresRestart:
          result['requiresRestart'] as bool? ?? _status.requiresRestart,
      message: result['message'] as String?,
    );
    await DownloaderSettingsService.setBinaryVersion(_status.version);
    await DownloaderSettingsService.setLatestBinaryVersion(
        _status.latestVersion);
    return _status;
  }

  Future<String?> getYtDlpPath() async {
    if (_status.ytDlpPath != null) {
      return _status.ytDlpPath;
    }
    await ensureBinariesAvailable();
    return _status.ytDlpPath;
  }

  Future<String?> getFfmpegPath() async {
    if (_status.ffmpegPath != null) {
      return _status.ffmpegPath;
    }
    await ensureBinariesAvailable();
    return _status.ffmpegPath;
  }
}
