import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:provider/provider.dart';

import '../../controller/downloader_controller.dart';
import '../widgets/quality_selector_dropdown.dart';
import '../widgets/video_preview_card.dart';

class VideoPreviewScreen extends StatelessWidget {
  const VideoPreviewScreen({super.key, required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final controller = context.watch<DownloaderController>();
    final state = controller.state;
    final videoInfo = state.videoInfo;

    return Scaffold(
      backgroundColor: theme.scaffoldBackgroundColor,
      appBar: AppBar(
        title: const Text('Download'),
        centerTitle: true,
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      body: videoInfo == null
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer
                          .withValues(alpha: 0.3),
                      shape: BoxShape.circle,
                    ),
                    child: state.isLoading
                        ? SizedBox(
                            width: 40,
                            height: 40,
                            child: CircularProgressIndicator(
                              strokeWidth: 3,
                              color: theme.colorScheme.primary,
                            ),
                          )
                        : Icon(
                            Icons.video_file_outlined,
                            size: 40,
                            color: theme.colorScheme.primary,
                          ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    state.isLoading
                        ? 'Fetching video info...'
                        : 'No video info loaded yet.',
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.mutedText,
                    ),
                  ),
                ],
              ),
            )
          : SafeArea(
              child: ListView(
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                children: [
                  VideoPreviewCard(videoInfo: videoInfo),
                  const SizedBox(height: 20),
                  QualitySelectorDropdown(
                    value: state.selectedQuality,
                    onChanged: (value) {
                      if (value != null) {
                        controller.selectQuality(value);
                      }
                    },
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: theme.subtleSurface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: theme.softBorder,
                        width: 1,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.info_outline_rounded,
                          size: 18,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'Video will be saved to your gallery',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.mutedText,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    height: 56,
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 200),
                      child: state.isBusy
                          ? FilledButton.icon(
                              onPressed: null,
                              icon: SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.5,
                                  color: theme.colorScheme.onPrimary,
                                ),
                              ),
                              label: const Text('Starting...'),
                              style: FilledButton.styleFrom(
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(18),
                                ),
                              ),
                            )
                          : FilledButton.icon(
                              onPressed: () async {
                                HapticFeedback.mediumImpact();
                                await controller.startDownload(
                                  url,
                                  quality: state.selectedQuality,
                                );
                                if (!context.mounted) {
                                  return;
                                }
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Row(
                                      children: [
                                        Icon(
                                          Icons.check_circle_outline_rounded,
                                          color: theme.colorScheme.onPrimary,
                                          size: 20,
                                        ),
                                        const SizedBox(width: 8),
                                        const Text('Download queued'),
                                      ],
                                    ),
                                    behavior: SnackBarBehavior.floating,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    backgroundColor: theme.colorScheme.primary,
                                  ),
                                );
                                Navigator.of(context).pop(true);
                              },
                              icon: const Icon(Icons.download_rounded),
                              label: const Text(
                                'Start Download',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              style: FilledButton.styleFrom(
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(18),
                                ),
                              ),
                            ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
