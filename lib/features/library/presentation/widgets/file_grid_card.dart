import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/file_item.dart';

class FileGridCard extends StatelessWidget {
  const FileGridCard({
    super.key,
    required this.file,
    required this.onTap,
    this.onPlay,
    this.onDelete,
    this.isPlaying = false,
  });

  final FileItem file;
  final VoidCallback onTap;
  final VoidCallback? onPlay;
  final VoidCallback? onDelete;
  final bool isPlaying;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final bool isAudio = file.isAudio;

    final Color badgeBg = isAudio
        ? (file.extension == 'flac' || file.extension == 'wav'
            ? theme.accentEmerald.withValues(alpha: 0.15)
            : theme.accentIndigo.withValues(alpha: 0.15))
        : theme.accentRose.withValues(alpha: 0.15);

    final Color badgeText = isAudio
        ? (file.extension == 'flac' || file.extension == 'wav'
            ? theme.accentEmerald
            : theme.accentIndigo)
        : theme.accentRose;

    return Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isPlaying
              ? scheme.primary
              : scheme.outlineVariant.withValues(alpha: theme.isDarkMode ? 0.4 : 0.7),
          width: isPlaying ? 1.5 : 1.0,
        ),
      ),
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            // Media thumbnail preview banner
            Expanded(
              flex: 5,
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: isAudio
                        ? <Color>[
                            const Color(0xFF1E1B4B),
                            const Color(0xFF312E81),
                          ]
                        : <Color>[
                            const Color(0xFF3B0764),
                            const Color(0xFF831843),
                          ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: Stack(
                  children: <Widget>[
                    Center(
                      child: Icon(
                        isAudio ? Icons.music_note_rounded : Icons.play_circle_filled_rounded,
                        color: Colors.white.withValues(alpha: 0.85),
                        size: 38,
                      ),
                    ),
                    Positioned(
                      top: 8,
                      left: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: badgeBg,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: badgeText.withValues(alpha: 0.3)),
                        ),
                        child: Text(
                          file.extension.toUpperCase(),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            color: badgeText,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ),
                    ),
                    if (file.durationMs > 0)
                      Positioned(
                        bottom: 8,
                        right: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.65),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            file.formattedDuration,
                            style: const TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                    if (isPlaying)
                      Positioned(
                        bottom: 8,
                        left: 8,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: theme.accentEmerald,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Row(
                            mainAxisSize: MainAxisSize.min,
                            children: <Widget>[
                              Icon(Icons.volume_up_rounded, size: 12, color: Colors.white),
                              SizedBox(width: 3),
                              Text(
                                'PLAYING',
                                style: TextStyle(
                                  fontSize: 9,
                                  fontWeight: FontWeight.w800,
                                  color: Colors.white,
                                  letterSpacing: 0.5,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            // Title & meta
            Expanded(
              flex: 4,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: <Widget>[
                    Text(
                      file.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        color: isPlaying ? scheme.primary : theme.strongText,
                      ),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: <Widget>[
                        Text(
                          file.formattedSize,
                          style: TextStyle(
                            fontSize: 11,
                            color: theme.mutedText,
                          ),
                        ),
                        if (onPlay != null)
                          Material(
                            color: Colors.transparent,
                            child: InkWell(
                              borderRadius: BorderRadius.circular(16),
                              onTap: onPlay,
                              child: Container(
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(
                                  color: scheme.primaryContainer.withValues(alpha: 0.6),
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                  size: 16,
                                  color: scheme.onPrimaryContainer,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
