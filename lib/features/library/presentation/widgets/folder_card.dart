import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/folder_item.dart';

class FolderCard extends StatelessWidget {
  const FolderCard({
    super.key,
    required this.folder,
    required this.onTap,
    this.onDelete,
  });

  final FolderItem folder;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  List<Color> _getGradient(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String lower = folder.name.toLowerCase();
    if (lower.contains('music') || lower.contains('synth') || lower.contains('rock') || lower.contains('lo-fi')) {
      return theme.folderMusicGradient;
    }
    if (lower.contains('video') || lower.contains('movie') || lower.contains('film') || lower.contains('reel')) {
      return theme.folderVideoGradient;
    }
    if (lower.contains('podcast') || lower.contains('show') || lower.contains('audio')) {
      return theme.folderPodcastGradient;
    }
    if (lower.contains('download') || lower.contains('fx') || lower.contains('sound')) {
      return theme.folderDownloadsGradient;
    }
    return theme.folderDefaultGradient;
  }

  IconData _getFolderIcon() {
    final String lower = folder.name.toLowerCase();
    if (lower.contains('music') || lower.contains('synth') || lower.contains('rock') || lower.contains('lo-fi')) {
      return Icons.library_music_rounded;
    }
    if (lower.contains('video') || lower.contains('movie') || lower.contains('film') || lower.contains('reel')) {
      return Icons.video_collection_rounded;
    }
    if (lower.contains('podcast') || lower.contains('show')) {
      return Icons.podcasts_rounded;
    }
    if (lower.contains('download') || lower.contains('fx')) {
      return Icons.folder_zip_rounded;
    }
    return Icons.folder_rounded;
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final List<Color> gradient = _getGradient(context);
    final IconData iconData = _getFolderIcon();

    final List<String> parts = <String>[];
    if (folder.subfolderCount > 0) {
      parts.add('${folder.subfolderCount} ${folder.subfolderCount == 1 ? "folder" : "folders"}');
    }
    if (folder.fileCount > 0) {
      parts.add('${folder.fileCount} ${folder.fileCount == 1 ? "file" : "files"}');
    }
    final String metaText = parts.isEmpty ? 'Empty' : parts.join(' • ');

    return Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: scheme.outlineVariant.withValues(alpha: theme.isDarkMode ? 0.4 : 0.7),
        ),
      ),
      child: InkWell(
        onTap: onTap,
        splashColor: gradient.first.withValues(alpha: 0.12),
        highlightColor: gradient.first.withValues(alpha: 0.06),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: <Widget>[
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: gradient,
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: <BoxShadow>[
                    BoxShadow(
                      color: gradient.first.withValues(alpha: 0.35),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Icon(
                  iconData,
                  color: Colors.white,
                  size: 24,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    Text(
                      folder.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14.5,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      metaText,
                      style: TextStyle(
                        fontSize: 12,
                        color: theme.mutedText,
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                  ],
                ),
              ),
              if (onDelete != null)
                IconButton(
                  icon: Icon(
                    Icons.more_horiz_rounded,
                    size: 20,
                    color: theme.faintText,
                  ),
                  tooltip: 'Folder options',
                  constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
                  onPressed: () {
                    showModalBottomSheet<void>(
                      context: context,
                      builder: (BuildContext ctx) {
                        return SafeArea(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            child: ListTile(
                              leading: const Icon(Icons.delete_outline, color: Colors.redAccent),
                              title: const Text('Delete Folder', style: TextStyle(color: Colors.redAccent)),
                              subtitle: Text('Deletes "${folder.name}" and all contents'),
                              onTap: () {
                                Navigator.pop(ctx);
                                onDelete?.call();
                              },
                            ),
                          ),
                        );
                      },
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }
}
