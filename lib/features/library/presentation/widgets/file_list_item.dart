import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/file_item.dart';

class FileListItem extends StatelessWidget {
  const FileListItem({
    super.key,
    required this.file,
    required this.onTap,
    this.onDelete,
    this.onPlay,
    this.isPlaying = false,
  });

  final FileItem file;
  final VoidCallback onTap;
  final VoidCallback? onDelete;
  final VoidCallback? onPlay;
  final bool isPlaying;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final bool isAudio = file.isAudio;

    final Color badgeBg = isAudio
        ? (file.extension == 'flac' || file.extension == 'wav'
            ? theme.accentEmerald.withValues(alpha: 0.12)
            : theme.accentIndigo.withValues(alpha: 0.12))
        : theme.accentRose.withValues(alpha: 0.12);

    final Color badgeText = isAudio
        ? (file.extension == 'flac' || file.extension == 'wav'
            ? theme.accentEmerald
            : theme.accentIndigo)
        : theme.accentRose;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: isPlaying
            ? scheme.primaryContainer.withValues(alpha: theme.isDarkMode ? 0.35 : 0.45)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isPlaying
              ? scheme.primary.withValues(alpha: 0.4)
              : Colors.transparent,
          width: 1,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: <Widget>[
                // Leading Icon Container
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: isPlaying
                        ? scheme.primary
                        : isAudio
                            ? scheme.primaryContainer.withValues(alpha: 0.5)
                            : scheme.tertiaryContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Stack(
                    alignment: Alignment.center,
                    children: <Widget>[
                      if (isPlaying)
                        const Icon(
                          Icons.equalizer_rounded,
                          color: Colors.white,
                          size: 22,
                        )
                      else
                        Icon(
                          isAudio ? Icons.music_note_rounded : Icons.movie_rounded,
                          color: isAudio ? scheme.primary : scheme.tertiary,
                          size: 22,
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 14),
                // Title and Meta
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        file.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontWeight: isPlaying ? FontWeight.w700 : FontWeight.w600,
                          fontSize: 14,
                          color: isPlaying ? scheme.primary : theme.strongText,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: <Widget>[
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 5,
                              vertical: 1.5,
                            ),
                            decoration: BoxDecoration(
                              color: badgeBg,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              file.extension.toUpperCase(),
                              style: TextStyle(
                                fontSize: 9.5,
                                fontWeight: FontWeight.w700,
                                color: badgeText,
                                letterSpacing: 0.4,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            file.formattedSize,
                            style: TextStyle(
                              fontSize: 12,
                              color: theme.mutedText,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                          if (file.durationMs > 0) ...<Widget>[
                            const SizedBox(width: 6),
                            Text(
                              '•',
                              style: TextStyle(
                                fontSize: 12,
                                color: theme.faintText,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              file.formattedDuration,
                              style: TextStyle(
                                fontSize: 12,
                                color: theme.mutedText,
                                fontWeight: FontWeight.w400,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
                // Action Buttons
                if (onPlay != null)
                  IconButton(
                    icon: Icon(
                      isPlaying ? Icons.pause_circle_filled_rounded : Icons.play_circle_fill_rounded,
                      color: isPlaying ? scheme.primary : scheme.outline,
                      size: 32,
                    ),
                    tooltip: isPlaying ? 'Pause' : 'Play',
                    constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
                    onPressed: onPlay,
                  ),
                if (onDelete != null)
                  PopupMenuButton<String>(
                    icon: Icon(
                      Icons.more_vert_rounded,
                      size: 20,
                      color: theme.faintText,
                    ),
                    tooltip: 'More actions',
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    itemBuilder: (BuildContext ctx) => <PopupMenuEntry<String>>[
                      const PopupMenuItem<String>(
                        value: 'delete',
                        child: Row(
                          children: <Widget>[
                            Icon(Icons.delete_outline, size: 18, color: Colors.redAccent),
                            SizedBox(width: 10),
                            Text('Delete file', style: TextStyle(color: Colors.redAccent)),
                          ],
                        ),
                      ),
                    ],
                    onSelected: (String val) {
                      if (val == 'delete') {
                        onDelete?.call();
                      }
                    },
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
