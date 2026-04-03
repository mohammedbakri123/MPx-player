import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

class EmptyReelsView extends StatelessWidget {
  final bool isCustomFolder;
  final VoidCallback? onImportFolder;
  final String? reelsFolderPath;

  const EmptyReelsView({
    super.key,
    required this.isCustomFolder,
    this.onImportFolder,
    this.reelsFolderPath,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.appBackground,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.video_library_rounded,
              size: 80,
              color: theme.faintText,
            ),
            const SizedBox(height: 16),
            Text(
              'No Reels yet!',
              style: theme.textTheme.headlineSmall
                  ?.copyWith(color: theme.strongText),
            ),
            const SizedBox(height: 8),
            Text(
              'Navigate to a folder in Home and tap the import button, or add videos via the video details sheet.',
              textAlign: TextAlign.center,
              style:
                  theme.textTheme.bodyMedium?.copyWith(color: theme.mutedText),
            ),
            if (!isCustomFolder && reelsFolderPath != null) ...[
              const SizedBox(height: 24),
              _buildFolderPathBox(context, theme),
            ],
            const SizedBox(height: 24),
            if (!isCustomFolder) _buildImportButton(context, theme),
          ],
        ),
      ),
    );
  }

  Widget _buildFolderPathBox(BuildContext context, ThemeData theme) {
    return Container(
      padding: const EdgeInsets.all(12),
      margin: const EdgeInsets.symmetric(horizontal: 24),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: theme.softBorder),
      ),
      child: Column(
        children: [
          Text(
            'Or add/delete your own videos externally at:',
            style: TextStyle(
                color: theme.strongText,
                fontWeight: FontWeight.w600,
                fontSize: 12),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 6),
          SelectableText(
            reelsFolderPath!,
            style: TextStyle(
              color: theme.colorScheme.primary,
              fontFamily: 'monospace',
              fontSize: 12,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildImportButton(BuildContext context, ThemeData theme) {
    return ElevatedButton.icon(
      onPressed: onImportFolder,
      icon: const Icon(Icons.folder_open_rounded),
      label: const Text('Import Current Folder'),
      style: ElevatedButton.styleFrom(
        backgroundColor: theme.colorScheme.primary,
        foregroundColor: theme.colorScheme.onPrimary,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(30),
        ),
      ),
    );
  }
}
