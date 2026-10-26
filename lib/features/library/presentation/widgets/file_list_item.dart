import 'package:flutter/material.dart';
import '../../models/file_item.dart';

class FileListItem extends StatelessWidget {
  const FileListItem({
    super.key,
    required this.file,
    required this.onTap,
    this.onDelete,
    this.onPlay,
  });

  final FileItem file;
  final VoidCallback onTap;
  final VoidCallback? onDelete;
  final VoidCallback? onPlay;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final bool isAudio = file.isAudio;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: <Widget>[
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: isAudio
                    ? scheme.primaryContainer.withValues(alpha: 0.6)
                    : scheme.tertiaryContainer.withValues(alpha: 0.6),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                isAudio ? Icons.music_note_rounded : Icons.movie_rounded,
                color: isAudio
                    ? scheme.primary
                    : scheme.tertiary,
                size: 22,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    file.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w500,
                      fontSize: 14,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Row(
                    children: <Widget>[
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 4,
                          vertical: 1,
                        ),
                        decoration: BoxDecoration(
                          color: scheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(3),
                        ),
                        child: Text(
                          file.extension.toUpperCase(),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        file.formattedSize,
                        style: TextStyle(
                          fontSize: 12,
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                      if (file.durationMs > 0) ...<Widget>[
                        const SizedBox(width: 6),
                        Text(
                          '•',
                          style: TextStyle(
                            fontSize: 12,
                            color: scheme.outline,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          file.formattedDuration,
                          style: TextStyle(
                            fontSize: 12,
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            IconButton(
              icon: Icon(
                Icons.play_circle_outline_rounded,
                color: scheme.primary,
                size: 24,
              ),
              tooltip: 'Play',
              onPressed: onPlay ?? onTap,
            ),
            if (onDelete != null)
              PopupMenuButton<String>(
                icon: Icon(
                  Icons.more_vert,
                  size: 18,
                  color: scheme.outline,
                ),
                itemBuilder: (BuildContext ctx) => <PopupMenuEntry<String>>[
                  const PopupMenuItem<String>(
                    value: 'delete',
                    child: Row(
                      children: <Widget>[
                        Icon(Icons.delete_outline, size: 18, color: Colors.redAccent),
                        SizedBox(width: 8),
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
    );
  }
}
