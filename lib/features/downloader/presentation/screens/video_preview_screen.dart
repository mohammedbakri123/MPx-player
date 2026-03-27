import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controller/downloader_controller.dart';
import '../widgets/quality_selector_dropdown.dart';
import '../widgets/video_preview_card.dart';

class VideoPreviewScreen extends StatelessWidget {
  const VideoPreviewScreen({super.key, required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<DownloaderController>();
    final state = controller.state;
    final videoInfo = state.videoInfo;

    return Scaffold(
      appBar: AppBar(title: const Text('Preview Download')),
      body: videoInfo == null
          ? const Center(child: Text('No video info loaded yet.'))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                VideoPreviewCard(videoInfo: videoInfo),
                const SizedBox(height: 16),
                QualitySelectorDropdown(
                  value: state.selectedQuality,
                  onChanged: (value) {
                    if (value != null) {
                      controller.selectQuality(value);
                    }
                  },
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: state.isBusy
                      ? null
                      : () async {
                          await controller.startDownload(
                            url,
                            quality: state.selectedQuality,
                          );
                          if (!context.mounted) {
                            return;
                          }
                          Navigator.of(context).pop(true);
                        },
                  icon: const Icon(Icons.download_rounded),
                  label: const Text('Start Download'),
                ),
              ],
            ),
    );
  }
}
