import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../../../../core/services/binary_manager.dart';
import '../../domain/enums/quality_preference.dart';
import '../../services/downloader_settings_service.dart';
import '../widgets/quality_selector_dropdown.dart';

class DownloaderSettingsScreen extends StatefulWidget {
  const DownloaderSettingsScreen({super.key});

  @override
  State<DownloaderSettingsScreen> createState() =>
      _DownloaderSettingsScreenState();
}

class _DownloaderSettingsScreenState extends State<DownloaderSettingsScreen> {
  late bool _autoUpdate;
  late bool _logsEnabled;
  late QualityPreference _quality;
  String? _cookiesPath;
  String? _downloadPath;
  bool _checking = false;
  bool _loadingStatus = true;

  @override
  void initState() {
    super.initState();
    _autoUpdate = DownloaderSettingsService.autoUpdateEnabled;
    _logsEnabled = DownloaderSettingsService.logsEnabled;
    _quality = DownloaderSettingsService.defaultQuality;
    _downloadPath = DownloaderSettingsService.downloadPath;
    _cookiesPath = DownloaderSettingsService.cookiesPath;
    _refreshStatus();
  }

  Future<void> _refreshStatus() async {
    try {
      await BinaryManager.instance.ensureBinariesAvailable();
    } catch (_) {
      // Platform channel may not be registered yet — fail silently.
    } finally {
      if (mounted) {
        setState(() => _loadingStatus = false);
      }
    }
  }

  Future<void> _pickCookies() async {
    final result = await FilePicker.platform.pickFiles();
    final path = result?.files.single.path;
    if (path == null) {
      return;
    }

    final supportDir = await getApplicationSupportDirectory();
    final cookiesDir = Directory(p.join(supportDir.path, 'downloader'));
    await cookiesDir.create(recursive: true);
    final target = File(p.join(cookiesDir.path, 'cookies.txt'));
    await File(path).copy(target.path);
    await DownloaderSettingsService.setCookiesPath(target.path);
    setState(() => _cookiesPath = target.path);
  }

  Future<void> _pickDownloadFolder() async {
    final messenger = ScaffoldMessenger.of(context);
    final selectedDir = await FilePicker.platform.getDirectoryPath(
      dialogTitle: 'Choose download folder',
    );
    if (selectedDir == null) return;

    await DownloaderSettingsService.setDownloadPath(selectedDir);
    if (mounted) {
      setState(() => _downloadPath = selectedDir);
      messenger.showSnackBar(
        const SnackBar(content: Text('Download folder saved.')),
      );
    }
  }

  Future<void> _checkNow() async {
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _checking = true);
    try {
      final status = await BinaryManager.instance.checkForUpdates();
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(status.message ?? 'Update check finished.'),
          ),
        );
        setState(() {});
      }
    } catch (error) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(content: Text(error.toString())),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _checking = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final binaryStatus = BinaryManager.instance.status;
    final versionText = binaryStatus.version ?? 'Unknown version';
    final latestText = binaryStatus.latestVersion ?? 'Unknown';

    return Scaffold(
      appBar: AppBar(title: const Text('Downloader Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile.adaptive(
            value: _autoUpdate,
            title: const Text('Auto-update yt-dlp engine'),
            onChanged: (value) async {
              await DownloaderSettingsService.setAutoUpdateEnabled(value);
              setState(() => _autoUpdate = value);
            },
          ),
          SwitchListTile.adaptive(
            value: _logsEnabled,
            title: const Text('Enable debug logs'),
            onChanged: (value) async {
              await DownloaderSettingsService.setLogsEnabled(value);
              setState(() => _logsEnabled = value);
            },
          ),
          const SizedBox(height: 12),
          QualitySelectorDropdown(
            value: _quality,
            onChanged: (value) async {
              if (value == null) {
                return;
              }
              await DownloaderSettingsService.setDefaultQuality(value);
              setState(() => _quality = value);
            },
          ),
          const SizedBox(height: 16),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.folder_outlined),
            title: const Text('Download folder'),
            subtitle: Text(
              _downloadPath ?? '/Movies/mpxReels',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: OutlinedButton.icon(
              onPressed: _pickDownloadFolder,
              icon: const Icon(Icons.drive_file_rename_outline, size: 18),
              label: const Text('Change'),
            ),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Cookies file'),
            subtitle: Text(
              _cookiesPath ??
                  'Not imported. Import cookies for Instagram or age-restricted content.',
            ),
            trailing: OutlinedButton(
              onPressed: _pickCookies,
              child: const Text('Import'),
            ),
          ),
          Card(
            margin: const EdgeInsets.only(top: 8),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Instagram cookies help',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'If Instagram says to import cookies, export a Netscape-format cookies.txt file from a browser where you are already logged in to Instagram, then tap Import above.',
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Quick path: log into Instagram in Firefox or Kiwi Browser -> use a cookie-export extension -> save cookies.txt -> import it here.',
                  ),
                ],
              ),
            ),
          ),
          const ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text('Share behavior'),
            subtitle: Text(
              'Shared links open a small approval window with quality choices and then show a progress notification instead of opening the full app.',
            ),
          ),
          const Divider(height: 28),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('yt-dlp engine'),
            subtitle: Text(versionText),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Latest known version'),
            subtitle: Text(latestText),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Engine status'),
            subtitle: Text(
              _loadingStatus
                  ? 'Checking...'
                  : binaryStatus.ytDlpAvailable
                      ? (binaryStatus.updateAvailable
                          ? 'Update available'
                          : 'Ready')
                      : 'Unavailable',
            ),
          ),
          FilledButton.icon(
            onPressed: _checking ? null : _checkNow,
            icon: _checking
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh_rounded),
            label: const Text('Update downloader engine'),
          ),
        ],
      ),
    );
  }
}
