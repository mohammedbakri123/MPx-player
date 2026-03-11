import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class HistoryEmptyState extends StatelessWidget {
  final VoidCallback? onBrowseVideos;

  const HistoryEmptyState({super.key, this.onBrowseVideos});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: const Color(0xFF7C3AED).withValues(
                  alpha: theme.isDarkMode ? 0.18 : 0.1,
                ),
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(
                Icons.history,
                size: 40,
                color: Color(0xFF7C3AED),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'No Watch History',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: theme.strongText,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Videos you play will appear here',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: theme.mutedText,
              ),
            ),
            const SizedBox(height: 24),
            if (onBrowseVideos != null)
              ElevatedButton.icon(
                onPressed: onBrowseVideos,
                icon: const Icon(Icons.video_library_outlined),
                label: const Text('Browse Videos'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.purple.shade500,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
