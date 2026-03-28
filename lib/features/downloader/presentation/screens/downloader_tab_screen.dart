import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controller/downloader_controller.dart';
import '../../services/downloader_settings_service.dart';
import '../widgets/url_input_field.dart';
import 'video_preview_screen.dart';

class DownloaderTabScreen extends StatefulWidget {
  const DownloaderTabScreen({
    super.key,
    this.initialUrl,
    this.autoSubmit = false,
    this.launchedFromShare = false,
  });

  final String? initialUrl;
  final bool autoSubmit;
  final bool launchedFromShare;

  @override
  State<DownloaderTabScreen> createState() => _DownloaderTabScreenState();
}

class _DownloaderTabScreenState extends State<DownloaderTabScreen> {
  late final TextEditingController _urlController;

  @override
  void initState() {
    super.initState();
    _urlController = TextEditingController();
    final initialUrl = widget.initialUrl?.trim();
    if (initialUrl != null && initialUrl.isNotEmpty) {
      _urlController.text = initialUrl;
    }
    if (widget.autoSubmit && initialUrl != null && initialUrl.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _submit();
        }
      });
    }
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final controller = context.read<DownloaderController>();
    final url = _urlController.text.trim();
    if (url.isEmpty) {
      return;
    }

    await controller.fetchVideoInfo(url);
    if (!mounted || controller.state.videoInfo == null) {
      return;
    }

    if (widget.launchedFromShare &&
        DownloaderSettingsService.autoDownloadSharedLinks) {
      await controller.startDownload(
        url,
        quality: controller.state.selectedQuality,
      );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Shared link queued for download.')),
      );
      Navigator.of(context).pop(true);
      return;
    }

    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => VideoPreviewScreen(url: url),
      ),
    );

    if (!mounted) return;

    if (result == true) {
      // Download started — pop back to DownloadsManagerScreen.
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<DownloaderController>().state;

    return Scaffold(
      appBar: AppBar(title: const Text('Add Download')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          UrlInputField(
            controller: _urlController,
            onSubmit: _submit,
            isBusy: state.isLoading,
          ),
          if (state.errorMessage != null) ...[
            const SizedBox(height: 12),
            Text(
              state.errorMessage!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
          const SizedBox(height: 20),
          Text(
            'Paste a supported video URL to preview metadata and choose quality before starting the download.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
