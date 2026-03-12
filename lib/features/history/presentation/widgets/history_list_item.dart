import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
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
    final theme = Theme.of(context);
    return Material(
      color: Colors.transparent,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: InkWell(
          onTap: isLoading ? null : onTap,
          borderRadius: BorderRadius.circular(24),
          child: Ink(
            decoration: BoxDecoration(
              color: theme.elevatedSurface,
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: theme.cardShadow,
                  blurRadius: 22,
                  offset: const Offset(0, 6),
                ),
              ],
              border: Border.all(color: theme.softBorder),
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
          child: const DecoratedBox(
            decoration: BoxDecoration(
              color: Color(0xFF7C3AED),
              borderRadius: BorderRadius.only(
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
      child: Material(
        color: const Color(0xFFDC2626).withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: isLoading ? null : onRemove,
          borderRadius: BorderRadius.circular(12),
          child: const Padding(
            padding: EdgeInsets.all(8),
            child: Icon(
              Icons.delete_outline,
              color: Colors.white,
              size: 18,
            ),
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
          color: const Color(0xFF7C3AED),
          borderRadius: BorderRadius.circular(999),
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
