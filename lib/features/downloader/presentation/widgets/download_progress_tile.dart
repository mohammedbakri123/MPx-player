import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

import '../../domain/entities/download_item.dart';
import '../../domain/enums/download_status.dart';

class DownloadProgressTile extends StatefulWidget {
  const DownloadProgressTile({
    super.key,
    required this.item,
    this.onPause,
    this.onResume,
    this.onCancel,
    this.onPlay,
    this.onDelete,
    this.onRetry,
  });

  final DownloadItem item;
  final VoidCallback? onPause;
  final VoidCallback? onResume;
  final VoidCallback? onCancel;
  final VoidCallback? onPlay;
  final VoidCallback? onDelete;
  final VoidCallback? onRetry;

  @override
  State<DownloadProgressTile> createState() => _DownloadProgressTileState();
}

class _DownloadProgressTileState extends State<DownloadProgressTile> {
  String? _fileSize;
  bool _expanded = false;

  @override
  void initState() {
    super.initState();
    _loadFileSize();
  }

  @override
  void didUpdateWidget(DownloadProgressTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.item.savePath != widget.item.savePath ||
        oldWidget.item.status != widget.item.status) {
      _loadFileSize();
    }
  }

  Future<void> _loadFileSize() async {
    final path = widget.item.savePath;
    if (path == null || widget.item.status != DownloadStatus.completed) {
      if (mounted) setState(() => _fileSize = null);
      return;
    }
    try {
      final file = File(path);
      if (await file.exists()) {
        final bytes = await file.length();
        if (mounted) setState(() => _fileSize = _formatBytes(bytes));
      } else {
        if (mounted) setState(() => _fileSize = null);
      }
    } catch (_) {
      if (mounted) setState(() => _fileSize = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final item = widget.item;
    final isComplete = item.status == DownloadStatus.completed;
    final isFailed = item.status == DownloadStatus.failed;
    final isActive = item.status == DownloadStatus.downloading ||
        item.status == DownloadStatus.queued ||
        item.status == DownloadStatus.paused;

    return GestureDetector(
      onTap: () {
        if (item.url.isNotEmpty || item.errorMessage != null) {
          setState(() => _expanded = !_expanded);
        }
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.only(bottom: 12),
        decoration: BoxDecoration(
          color: theme.cardTheme.color ?? theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isFailed
                ? theme.colorScheme.error.withValues(alpha: 0.3)
                : theme.softBorder,
            width: 1,
          ),
          boxShadow: [
            BoxShadow(
              color: theme.cardShadow,
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header row with icon and title
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: _statusColor(theme, item.status)
                          .withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Center(
                      child: isComplete
                          ? Icon(
                              item.savePath != null
                                  ? Icons.video_library_rounded
                                  : Icons.check_circle_rounded,
                              color: _statusColor(theme, item.status),
                              size: 22,
                            )
                          : isFailed
                              ? Icon(
                                  Icons.error_outline_rounded,
                                  color: theme.colorScheme.error,
                                  size: 22,
                                )
                              : SizedBox(
                                  width: 22,
                                  height: 22,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2.5,
                                    value: item.status == DownloadStatus.queued
                                        ? null
                                        : item.progress,
                                    color: theme.colorScheme.primary,
                                    backgroundColor: theme.colorScheme.primary
                                        .withValues(alpha: 0.15),
                                  ),
                                ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            _buildStatusBadge(theme, item.status),
                            if (_fileSize != null) ...[
                              const SizedBox(width: 8),
                              Icon(
                                Icons.sd_storage_rounded,
                                size: 12,
                                color: theme.faintText,
                              ),
                              const SizedBox(width: 3),
                              Text(
                                _fileSize!,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.faintText,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                            if (item.completedAt != null) ...[
                              const SizedBox(width: 8),
                              Icon(
                                Icons.schedule_rounded,
                                size: 12,
                                color: theme.faintText,
                              ),
                              const SizedBox(width: 3),
                              Text(
                                _formatTime(item.completedAt!),
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.faintText,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ],
                    ),
                  ),
                  if (_expanded)
                    Icon(
                      Icons.expand_less_rounded,
                      color: theme.faintText,
                      size: 20,
                    )
                  else if (item.url.isNotEmpty || item.errorMessage != null)
                    Icon(
                      Icons.expand_more_rounded,
                      color: theme.faintText,
                      size: 20,
                    ),
                ],
              ),

              // Progress bar for active downloads
              if (isActive) ...[
                const SizedBox(height: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: item.status == DownloadStatus.queued
                        ? null
                        : item.progress,
                    minHeight: 6,
                    backgroundColor:
                        theme.colorScheme.primary.withValues(alpha: 0.12),
                    valueColor: AlwaysStoppedAnimation<Color>(
                      theme.colorScheme.primary,
                    ),
                  ),
                ),
                if (item.status == DownloadStatus.downloading) ...[
                  const SizedBox(height: 6),
                  Text(
                    '${(item.progress * 100).toStringAsFixed(0)}%',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ],

              // Error message for failed downloads
              if (isFailed && item.errorMessage != null) ...[
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.error.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.warning_amber_rounded,
                        size: 16,
                        color: theme.colorScheme.error,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          item.errorMessage!,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.error,
                          ),
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              // Expanded details
              if (_expanded) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: theme.subtleSurface,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildDetailRow(
                        context,
                        icon: Icons.link_rounded,
                        label: 'URL',
                        value: item.url,
                        onTap: () {
                          Clipboard.setData(ClipboardData(text: item.url));
                          if (mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('URL copied')),
                            );
                          }
                        },
                      ),
                      if (item.savePath != null) ...[
                        const SizedBox(height: 8),
                        _buildDetailRow(
                          context,
                          icon: Icons.folder_rounded,
                          label: 'Path',
                          value: item.savePath!,
                        ),
                      ],
                      if (item.formatSelector != null) ...[
                        const SizedBox(height: 8),
                        _buildDetailRow(
                          context,
                          icon: Icons.high_quality_rounded,
                          label: 'Format',
                          value: item.formatSelector!,
                        ),
                      ],
                      const SizedBox(height: 8),
                      _buildDetailRow(
                        context,
                        icon: Icons.access_time_rounded,
                        label: 'Added',
                        value: _formatFullTime(item.addedAt),
                      ),
                    ],
                  ),
                ),
              ],

              // Action buttons
              const SizedBox(height: 12),
              _buildActions(theme, item),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(
    BuildContext context, {
    required IconData icon,
    required String label,
    required String value,
    VoidCallback? onTap,
  }) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 14, color: theme.mutedText),
            const SizedBox(width: 6),
            SizedBox(
              width: 50,
              child: Text(
                label,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.faintText,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Expanded(
              child: Text(
                value,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.mutedText,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (onTap != null)
              Icon(Icons.copy_rounded, size: 14, color: theme.faintText),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusBadge(ThemeData theme, DownloadStatus status) {
    Color color;
    String label;
    IconData icon;

    switch (status) {
      case DownloadStatus.completed:
        color = Colors.green;
        label = 'Completed';
        icon = Icons.check_circle_rounded;
        break;
      case DownloadStatus.downloading:
        color = theme.colorScheme.primary;
        label = 'Downloading';
        icon = Icons.download_rounded;
        break;
      case DownloadStatus.queued:
        color = theme.colorScheme.tertiary;
        label = 'Queued';
        icon = Icons.hourglass_empty_rounded;
        break;
      case DownloadStatus.paused:
        color = Colors.orange;
        label = 'Paused';
        icon = Icons.pause_circle_rounded;
        break;
      case DownloadStatus.failed:
        color = theme.colorScheme.error;
        label = 'Failed';
        icon = Icons.error_rounded;
        break;
      case DownloadStatus.cancelled:
        color = theme.faintText;
        label = 'Cancelled';
        icon = Icons.cancel_rounded;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActions(ThemeData theme, DownloadItem item) {
    final isComplete = item.status == DownloadStatus.completed;
    final isFailed = item.status == DownloadStatus.failed;
    final isActive = item.status == DownloadStatus.downloading ||
        item.status == DownloadStatus.queued ||
        item.status == DownloadStatus.paused;

    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (isActive) ...[
          if (item.status == DownloadStatus.downloading)
            OutlinedButton.icon(
              onPressed: widget.onPause,
              icon: const Icon(Icons.pause_rounded, size: 18),
              label: const Text('Pause'),
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              ),
            ),
          if (item.status == DownloadStatus.paused)
            FilledButton.icon(
              onPressed: widget.onResume,
              icon: const Icon(Icons.play_arrow_rounded, size: 18),
              label: const Text('Resume'),
              style: FilledButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              ),
            ),
          const SizedBox(width: 8),
          TextButton.icon(
            onPressed: widget.onCancel,
            icon: const Icon(Icons.close_rounded, size: 16),
            label: const Text('Cancel'),
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
            ),
          ),
        ],
        if (isFailed) ...[
          FilledButton.icon(
            onPressed: widget.onRetry,
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const Text('Retry'),
            style: FilledButton.styleFrom(
              backgroundColor: theme.colorScheme.primary,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            ),
          ),
          const SizedBox(width: 8),
          TextButton.icon(
            onPressed: widget.onDelete,
            icon: const Icon(Icons.delete_outline_rounded, size: 16),
            label: const Text('Delete'),
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
            ),
          ),
        ],
        if (isComplete) ...[
          if (item.savePath != null)
            FilledButton.icon(
              onPressed: widget.onPlay,
              icon: const Icon(Icons.play_circle_fill_rounded, size: 18),
              label: const Text('Play'),
              style: FilledButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              ),
            ),
          const SizedBox(width: 8),
          OutlinedButton.icon(
            onPressed: widget.onDelete,
            icon: const Icon(Icons.delete_outline_rounded, size: 16),
            label: const Text('Delete'),
            style: OutlinedButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
              side: BorderSide(
                color: theme.colorScheme.error.withValues(alpha: 0.3),
              ),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            ),
          ),
        ],
      ],
    );
  }

  Color _statusColor(ThemeData theme, DownloadStatus status) {
    switch (status) {
      case DownloadStatus.completed:
        return Colors.green;
      case DownloadStatus.downloading:
        return theme.colorScheme.primary;
      case DownloadStatus.queued:
        return theme.colorScheme.tertiary;
      case DownloadStatus.paused:
        return Colors.orange;
      case DownloadStatus.failed:
        return theme.colorScheme.error;
      case DownloadStatus.cancelled:
        return theme.faintText;
    }
  }

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1048576) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1073741824) return '${(bytes / 1048576).toStringAsFixed(1)} MB';
    return '${(bytes / 1073741824).toStringAsFixed(2)} GB';
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final diff = now.difference(time);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inHours < 1) return '${diff.inMinutes}m ago';
    if (diff.inDays < 1) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return '${time.day}/${time.month}/${time.year}';
  }

  String _formatFullTime(DateTime time) {
    return '${time.day}/${time.month}/${time.year} '
        '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }
}
