import 'package:flutter/material.dart';
import 'package:mpx/features/library/domain/entities/video_folder.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class FolderGridCard extends StatelessWidget {
  final VideoFolder folder;
  final VoidCallback onTap;

  const FolderGridCard({
    super.key,
    required this.folder,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final accent = theme.colorScheme.primary;
    final secondary = theme.colorScheme.secondary;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Ink(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: theme.elevatedSurface,
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: theme.softBorder),
            boxShadow: [
              BoxShadow(
                color: theme.cardShadow,
                blurRadius: 20,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      accent.withValues(alpha: 0.2),
                      secondary.withValues(alpha: 0.14),
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color:
                        accent.withValues(alpha: theme.isDarkMode ? 0.18 : 0.1),
                  ),
                ),
                child: Icon(Icons.folder_rounded, color: accent, size: 32),
              ),
              const SizedBox(height: 12),
              Text(
                folder.name,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w800,
                  color: theme.strongText,
                  letterSpacing: -0.2,
                ),
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 6,
                runSpacing: 6,
                children: [
                  _GridMetaPill(
                      label: '${folder.videoCount} videos', tint: accent),
                  _GridMetaPill(label: folder.formattedSize, tint: secondary),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GridMetaPill extends StatelessWidget {
  final String label;
  final Color tint;

  const _GridMetaPill({required this.label, required this.tint});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: tint.withValues(alpha: theme.isDarkMode ? 0.18 : 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: tint,
        ),
      ),
    );
  }
}
