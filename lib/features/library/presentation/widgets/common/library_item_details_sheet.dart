import 'package:provider/provider.dart';
import '../../../../reels/controllers/reels_controller.dart';
import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

import '../../../domain/entities/file_item.dart';
import '../../../domain/entities/video_file.dart';
import '../../../services/video_metadata_service.dart';
import 'library_item_ui.dart';

class LibraryItemDetailsSheet {
  static Future<void> showForItem(
    BuildContext context,
    FileItem item, {
    bool isFavorite = false,
    VoidCallback? onToggleFavorite,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _DetailsSheet(
        title: item.name,
        subtitle: item.isDirectory ? 'Folder' : 'Video',
        accentColor: item.isDirectory
            ? Theme.of(context).colorScheme.primary
            : Theme.of(context).colorScheme.secondary,
        icon: item.isDirectory ? Icons.folder_rounded : Icons.movie_rounded,
        primaryMeta: item.isDirectory
            ? LibraryItemUi.folderVideoLabel(item.videoCount)
            : item.formattedSize,
        secondaryMeta: _formatDateTime(item.modified),
        action: !item.isDirectory && onToggleFavorite != null
            ? _SheetAction(
                label: isFavorite ? 'Remove favorite' : 'Add favorite',
                icon: isFavorite ? Icons.favorite : Icons.favorite_border,
                onTap: onToggleFavorite,
              )
            : null,
        facts: [
          _SheetFact(
            label: 'Location',
            value: LibraryItemUi.parentFolderName(item.path),
          ),
          _SheetFact(
            label: item.isDirectory ? 'Contains' : 'Format',
            value: item.isDirectory
                ? _folderContainsLabel(item.videoCount)
                : item.extension.toUpperCase(),
          ),
          _SheetFact(label: 'Updated', value: _formatDateTime(item.modified)),
          _SheetFact(label: 'Path', value: item.path),
        ],
        extraContent: item.isDirectory
            ? null
            : _VideoRuntimeMetadata(
                path: item.path,
                fallbackDuration: '00:00',
                fallbackResolution: 'Unknown',
              ),
        onAddToReels: !item.isDirectory
            ? () async {
                final reelsController =
                    Provider.of<ReelsController>(context, listen: false);
                try {
                  await reelsController.importVideoToReels(item.path);
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                          content: Text('Video added to Reels'),
                          backgroundColor: Colors.green),
                    );
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                          content: Text('Failed to add to Reels: $e'),
                          backgroundColor: Colors.red),
                    );
                  }
                }
              }
            : null,
      ),
    );
  }

  static Future<void> showForVideo(
    BuildContext context,
    VideoFile video, {
    required bool isFavorite,
    required VoidCallback onToggleFavorite,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _DetailsSheet(
        title: video.title,
        subtitle: video.folderName,
        accentColor: Theme.of(context).colorScheme.secondary,
        icon: Icons.play_circle_fill_rounded,
        primaryMeta: video.formattedSize,
        secondaryMeta: video.formattedDate,
        action: _SheetAction(
          label: isFavorite ? 'Remove favorite' : 'Add favorite',
          icon: isFavorite ? Icons.favorite : Icons.favorite_border,
          onTap: onToggleFavorite,
        ),
        facts: [
          _SheetFact(label: 'Folder', value: video.folderName),
          _SheetFact(label: 'Quality', value: video.resolution),
          _SheetFact(label: 'Added', value: _formatDateTime(video.dateAdded)),
          _SheetFact(label: 'Path', value: video.path),
        ],
        extraContent: _VideoRuntimeMetadata(
          path: video.path,
          fallbackDuration: video.formattedDuration,
          fallbackResolution: video.resolution,
        ),
        onAddToReels: () async {
          final reelsController =
              Provider.of<ReelsController>(context, listen: false);
          try {
            await reelsController.importVideoToReels(video.path);
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                    content: Text('Video added to Reels'),
                    backgroundColor: Colors.green),
              );
            }
          } catch (e) {
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                    content: Text('Failed to add to Reels: $e'),
                    backgroundColor: Colors.red),
              );
            }
          }
        },
      ),
    );
  }

  static String _folderContainsLabel(int? count) {
    return LibraryItemUi.folderVideoLabel(count);
  }

  static String _formatDateTime(DateTime date) {
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];

    final local = date.toLocal();
    final month = months[local.month - 1];
    final day = local.day.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '$day $month ${local.year} • $hour:$minute';
  }
}

class _DetailsSheet extends StatelessWidget {
  final String title;
  final String subtitle;
  final String primaryMeta;
  final String secondaryMeta;
  final IconData icon;
  final Color accentColor;
  final _SheetAction? action;
  final VoidCallback? onAddToReels; // Add property
  final List<_SheetFact> facts;
  final Widget? extraContent;

  const _DetailsSheet({
    required this.title,
    required this.subtitle,
    required this.primaryMeta,
    required this.secondaryMeta,
    required this.icon,
    required this.accentColor,
    required this.facts,
    this.action,
    this.onAddToReels, // Initialize property
    this.extraContent,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: theme.appBackground,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: theme.faintText,
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          accentColor.withValues(alpha: 0.18),
                          accentColor.withValues(alpha: 0.08),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(
                        color: accentColor.withValues(
                          alpha: theme.isDarkMode ? 0.18 : 0.10,
                        ),
                      ),
                    ),
                    child: Icon(icon, color: accentColor, size: 28),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: theme.strongText,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          subtitle,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: theme.mutedText,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _MetaBadge(label: primaryMeta, accentColor: accentColor),
                  _MetaBadge(
                    label: secondaryMeta,
                    accentColor: theme.strongText,
                  ),
                ],
              ),
              if (extraContent != null) ...[
                const SizedBox(height: 14),
                extraContent!,
              ],
              const SizedBox(height: 18),
              Text(
                'Details',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: theme.strongText,
                ),
              ),
              const SizedBox(height: 10),
              ...facts.map((fact) => _FactRow(fact: fact)),
              if (action != null) ...[
                const SizedBox(height: 18),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () {
                      Navigator.pop(context);
                      action!.onTap();
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: accentColor,
                      foregroundColor: theme.colorScheme.surface,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(18),
                      ),
                    ),
                    icon: Icon(action!.icon, size: 18),
                    label: Text(action!.label),
                  ),
                ),
              ],
              if (onAddToReels != null) ...[
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(context);
                      onAddToReels!();
                    },
                    style: OutlinedButton.styleFrom(
                      foregroundColor: theme.strongText,
                      side: BorderSide(color: theme.softBorder),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(18),
                      ),
                    ),
                    icon: const Icon(Icons.video_collection_rounded, size: 18),
                    label: const Text('Add to Reels'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _VideoRuntimeMetadata extends StatefulWidget {
  final String path;
  final String fallbackDuration;
  final String fallbackResolution;

  const _VideoRuntimeMetadata({
    required this.path,
    required this.fallbackDuration,
    required this.fallbackResolution,
  });

  @override
  State<_VideoRuntimeMetadata> createState() => _VideoRuntimeMetadataState();
}

class _VideoRuntimeMetadataState extends State<_VideoRuntimeMetadata> {
  String? _resolution;
  String? _duration;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _resolution = widget.fallbackResolution;
    _duration = widget.fallbackDuration;
    _load();
  }

  Future<void> _load() async {
    try {
      final metadata =
          await VideoMetadataService().extractMetadata(widget.path);
      if (!mounted) return;

      setState(() {
        if (metadata?.height != null) {
          _resolution = _formatResolution(metadata!.height!);
        }
        if (metadata?.duration != null) {
          _duration = _formatDuration(metadata!.duration!);
        }
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _InfoTile(
            label: 'Resolution',
            value: _isLoading ? 'Loading...' : (_resolution ?? 'Unknown'),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _InfoTile(
            label: 'Duration',
            value: _isLoading ? 'Loading...' : (_duration ?? '00:00'),
          ),
        ),
      ],
    );
  }

  String _formatResolution(int height) {
    if (height >= 2160) return '4K';
    if (height >= 1080) return '1080P';
    if (height >= 720) return '720P';
    if (height >= 480) return '480P';
    return '${height}P';
  }

  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }

    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
}

class _MetaBadge extends StatelessWidget {
  final String label;
  final Color accentColor;

  const _MetaBadge({required this.label, required this.accentColor});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: accentColor.withValues(alpha: theme.isDarkMode ? 0.18 : 0.10),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: accentColor.withValues(alpha: theme.isDarkMode ? 0.18 : 0.08),
        ),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          color: accentColor,
        ),
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  final String label;
  final String value;

  const _InfoTile({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: theme.softBorder),
        boxShadow: [
          BoxShadow(
            color: theme.cardShadow
                .withValues(alpha: theme.isDarkMode ? 0.12 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: theme.mutedText,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: theme.strongText,
            ),
          ),
        ],
      ),
    );
  }
}

class _FactRow extends StatelessWidget {
  final _SheetFact fact;

  const _FactRow({required this.fact});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: theme.softBorder),
        boxShadow: [
          BoxShadow(
            color: theme.cardShadow
                .withValues(alpha: theme.isDarkMode ? 0.12 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 76,
            child: Text(
              fact.label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: theme.mutedText,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              fact.value,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: theme.strongText,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SheetAction {
  final String label;
  final IconData icon;
  final VoidCallback onTap;

  const _SheetAction({
    required this.label,
    required this.icon,
    required this.onTap,
  });
}

class _SheetFact {
  final String label;
  final String value;

  const _SheetFact({required this.label, required this.value});
}
