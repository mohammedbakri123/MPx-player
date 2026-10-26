import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/file_item.dart';

class MiniPlayerDock extends StatelessWidget {
  const MiniPlayerDock({
    super.key,
    required this.file,
    required this.isPlaying,
    required this.progress,
    required this.onTogglePlay,
    required this.onClose,
    required this.onTap,
  });

  final FileItem file;
  final bool isPlaying;
  final double progress;
  final VoidCallback onTogglePlay;
  final VoidCallback onClose;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final bool isAudio = file.isAudio;

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: theme.isDarkMode ? 0.4 : 0.8),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: theme.cardShadow,
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          // Progress line
          LinearProgressIndicator(
            value: progress,
            minHeight: 2.5,
            backgroundColor: scheme.surfaceContainerHighest,
            valueColor: AlwaysStoppedAnimation<Color>(
              isAudio ? theme.accentEmerald : theme.accentRose,
            ),
          ),
          InkWell(
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: <Widget>[
                  // Emblem icon
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: isAudio
                            ? <Color>[const Color(0xFF4F46E5), const Color(0xFF7C3AED)]
                            : <Color>[const Color(0xFFE11D48), const Color(0xFFF43F5E)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      isAudio ? Icons.music_note_rounded : Icons.videocam_rounded,
                      color: Colors.white,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  // Title and status
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          file.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 13.5,
                            letterSpacing: -0.1,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Row(
                          children: <Widget>[
                            Text(
                              isPlaying ? 'Playing • ' : 'Paused • ',
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w600,
                                color: isPlaying ? theme.accentEmerald : theme.mutedText,
                              ),
                            ),
                            Text(
                              '${file.extension.toUpperCase()} • ${file.formattedDuration}',
                              style: TextStyle(
                                fontSize: 11.5,
                                color: theme.mutedText,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  // Play/Pause button
                  IconButton(
                    icon: Icon(
                      isPlaying
                          ? Icons.pause_circle_filled_rounded
                          : Icons.play_circle_fill_rounded,
                      color: scheme.primary,
                      size: 34,
                    ),
                    tooltip: isPlaying ? 'Pause' : 'Play',
                    constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
                    onPressed: onTogglePlay,
                  ),
                  // Dismiss button
                  IconButton(
                    icon: Icon(
                      Icons.close_rounded,
                      color: theme.faintText,
                      size: 20,
                    ),
                    tooltip: 'Close player',
                    constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                    onPressed: onClose,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
