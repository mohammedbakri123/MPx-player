import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../domain/entities/watch_history_entry.dart';

class HistoryInfo extends StatelessWidget {
  final WatchHistoryEntry entry;

  const HistoryInfo({super.key, required this.entry});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            entry.video?.title ?? 'Unknown Video',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: theme.strongText,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.folder_outlined, size: 14, color: theme.mutedText),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  entry.video?.folderName ?? '',
                  style: TextStyle(
                    fontSize: 13,
                    color: theme.mutedText,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _buildInfoChip(
                context,
                icon: Icons.access_time,
                label: entry.formattedLastPlayed,
              ),
              const SizedBox(width: 12),
              if (!entry.isCompleted)
                _buildInfoChip(
                  context,
                  icon: Icons.play_arrow,
                  label: entry.formattedProgress,
                ),
              if (entry.isCompleted) ...[
                const SizedBox(width: 12),
                _buildCompletedChip(),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildInfoChip(BuildContext context,
      {required IconData icon, required String label}) {
    final theme = Theme.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: theme.faintText),
        const SizedBox(width: 4),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            color: theme.mutedText,
          ),
        ),
      ],
    );
  }

  Widget _buildCompletedChip() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.green.shade50,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle, size: 12, color: Colors.green.shade600),
          const SizedBox(width: 4),
          Text(
            'Watched',
            style: TextStyle(
              fontSize: 11,
              color: Colors.green.shade600,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}
