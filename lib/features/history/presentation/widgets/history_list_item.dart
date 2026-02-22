import 'package:flutter/material.dart';
import '../../domain/entities/watch_history_entry.dart';
import 'history_thumbnail.dart';
import 'history_info.dart';

class HistoryListItem extends StatelessWidget {
  final WatchHistoryEntry entry;
  final VoidCallback? onTap;
  final VoidCallback? onRemove;
  final bool isLoading;

  const HistoryListItem({
    super.key,
    required this.entry,
    this.onTap,
    this.onRemove,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
          border: Border.all(color: Colors.grey.shade100),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                HistoryThumbnail(entry: entry),
                if (entry.isInProgress) _buildProgressBar(),
                if (onRemove != null) _buildRemoveButton(),
                if (entry.isInProgress && !entry.isCompleted)
                  _buildResumeBadge(),
              ],
            ),
            HistoryInfo(entry: entry),
          ],
        ),
      ),
    );
  }

  Widget _buildProgressBar() {
    return Positioned(
      bottom: 0,
      left: 0,
      right: 0,
      child: Container(
        height: 4,
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.3),
          borderRadius: const BorderRadius.only(
            bottomLeft: Radius.circular(12),
            bottomRight: Radius.circular(12),
          ),
        ),
        child: FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: entry.progressFraction.clamp(0.0, 1.0),
          child: Container(
            decoration: BoxDecoration(
              color: Colors.purple.shade500,
              borderRadius: const BorderRadius.only(
                bottomLeft: Radius.circular(12),
                bottomRight: Radius.circular(4),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRemoveButton() {
    return Positioned(
      top: 8,
      right: 8,
      child: GestureDetector(
        onTap: isLoading ? null : onRemove,
        child: Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: Colors.red.withValues(alpha: 0.9),
            borderRadius: BorderRadius.circular(8),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.2),
                blurRadius: 4,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: const Icon(
            Icons.delete_outline,
            color: Colors.white,
            size: 18,
          ),
        ),
      ),
    );
  }

  Widget _buildResumeBadge() {
    return Positioned(
      top: 8,
      left: 8,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: Colors.purple.shade500,
          borderRadius: BorderRadius.circular(12),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.play_arrow, color: Colors.white, size: 14),
            SizedBox(width: 2),
            Text(
              'Resume',
              style: TextStyle(
                color: Colors.white,
                fontSize: 10,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
