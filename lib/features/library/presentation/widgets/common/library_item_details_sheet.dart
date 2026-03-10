import 'package:flutter/material.dart';

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
            ? const Color(0xFF2563EB)
            : const Color(0xFFEA580C),
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
        accentColor: const Color(0xFFEA580C),
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
    this.extraContent,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFFF8FAFC),
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
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
                    color: Colors.black.withValues(alpha: 0.12),
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
                      color: accentColor.withValues(alpha: 0.14),
                      borderRadius: BorderRadius.circular(18),
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
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          subtitle,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFF64748B),
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
                      accentColor: const Color(0xFF0F172A)),
                ],
              ),
              if (extraContent != null) ...[
                const SizedBox(height: 14),
                extraContent!,
              ],
              const SizedBox(height: 18),
              const Text(
                'Details',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF0F172A),
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
                      backgroundColor: const Color(0xFF0F172A),
                      foregroundColor: Colors.white,
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
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: accentColor.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(999),
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
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: Color(0xFF0F172A),
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
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 76,
            child: Text(
              fact.label,
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Color(0xFF64748B),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              fact.value,
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Color(0xFF0F172A),
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
