import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:provider/provider.dart';

import '../../controller/downloader_controller.dart';
import '../../domain/entities/video_info.dart';
import '../../domain/enums/quality_preference.dart';
import '../widgets/quality_selector_dropdown.dart';
import '../widgets/video_preview_card.dart';

class VideoPreviewScreen extends StatefulWidget {
  const VideoPreviewScreen({super.key, required this.url});

  final String url;

  @override
  State<VideoPreviewScreen> createState() => _VideoPreviewScreenState();
}

class _VideoPreviewScreenState extends State<VideoPreviewScreen> {
  late TextEditingController _titleController;
  bool _isEditingTitle = false;

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController();
  }

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  void _syncTitleFromController(VideoInfo? videoInfo) {
    if (videoInfo != null && !_isEditingTitle) {
      final currentText = _titleController.text;
      if (currentText.isEmpty || currentText == 'Untitled') {
        _titleController.text = videoInfo.title;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final controller = context.watch<DownloaderController>();
    final state = controller.state;
    final videoInfo = state.videoInfo;

    _syncTitleFromController(videoInfo);

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
                  const SizedBox(height: 16),
                  _buildFileSizeBadge(videoInfo, state.selectedQuality),
                  const SizedBox(height: 16),
                  _buildEditableTitle(context, controller, videoInfo),
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
                                  widget.url,
                                  quality: state.selectedQuality,
                                  title: _titleController.text.isNotEmpty
                                      ? _titleController.text
                                      : null,
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

  Widget _buildFileSizeBadge(VideoInfo videoInfo, QualityPreference quality) {
    final theme = Theme.of(context);
    final size = _estimateFileSize(videoInfo, quality);
    if (size == null || size == 0) {
      return const SizedBox.shrink();
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.softBorder),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.folder_open_rounded,
            size: 16,
            color: theme.mutedText,
          ),
          const SizedBox(width: 6),
          Text(
            'Estimated size: ${_formatFileSize(size)}',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: theme.mutedText,
            ),
          ),
        ],
      ),
    );
  }

  int? _estimateFileSize(VideoInfo videoInfo, QualityPreference quality) {
    if (videoInfo.formats.isEmpty) {
      return videoInfo.fileSizeApprox;
    }

    final formatsWithSize = videoInfo.formats
        .where((f) => f.fileSize != null && f.fileSize! > 0)
        .toList();

    if (formatsWithSize.isEmpty) {
      return videoInfo.fileSizeApprox;
    }

    int? maxHeight;
    switch (quality) {
      case QualityPreference.p1080:
        maxHeight = 1080;
      case QualityPreference.p720:
        maxHeight = 720;
      case QualityPreference.p480:
        maxHeight = 480;
      case QualityPreference.auto:
      case QualityPreference.audioOnly:
        break;
    }

    if (quality == QualityPreference.audioOnly) {
      final audioFormats = formatsWithSize
          .where((f) => f.height == null || f.height == 0)
          .toList();
      if (audioFormats.isNotEmpty) {
        int max = 0;
        for (final f in audioFormats) {
          if (f.fileSize! > max) max = f.fileSize!;
        }
        return max;
      }
    }

    if (maxHeight != null) {
      final maxH = maxHeight;
      final matchingFormats = formatsWithSize
          .where((f) => f.height != null && f.height! <= maxH)
          .toList();
      if (matchingFormats.isNotEmpty) {
        int max = 0;
        for (final f in matchingFormats) {
          if (f.fileSize! > max) max = f.fileSize!;
        }
        return max;
      }
    }

    int max = 0;
    for (final f in formatsWithSize) {
      if (f.fileSize! > max) max = f.fileSize!;
    }
    return max;
  }

  Widget _buildEditableTitle(
    BuildContext context,
    DownloaderController controller,
    VideoInfo videoInfo,
  ) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: _isEditingTitle ? theme.colorScheme.primary : theme.softBorder,
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _titleController,
              enabled: _isEditingTitle,
              maxLines: 2,
              minLines: 1,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: theme.strongText,
              ),
              decoration: InputDecoration(
                hintText: 'Video title',
                hintStyle: TextStyle(color: theme.faintText, fontSize: 14),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 12,
                ),
              ),
              onChanged: (value) {
                controller.setCustomTitle(value);
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              onTap: () {
                setState(() {
                  _isEditingTitle = !_isEditingTitle;
                  if (!_isEditingTitle) {
                    _titleController.text = videoInfo.title;
                    controller.setCustomTitle(null);
                  }
                });
              },
              child: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: _isEditingTitle
                      ? theme.colorScheme.primary.withValues(alpha: 0.1)
                      : theme.subtleSurface,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  _isEditingTitle ? Icons.check_rounded : Icons.edit_rounded,
                  size: 16,
                  color: _isEditingTitle
                      ? theme.colorScheme.primary
                      : theme.mutedText,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _formatFileSize(int? bytes) {
    if (bytes == null || bytes == 0) return 'Unknown';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
