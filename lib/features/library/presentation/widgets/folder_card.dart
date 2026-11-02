import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../../../core/theme/mpx_ui_tokens.dart';
import '../../models/folder_item.dart';

/// Folder row, MX structure in MPx's own code.
///
/// A plain grey folder thumb, name, and counts — no
/// per-folder gradient colours, as in MX's folder list.
/// There is no size or "new items" badge data in MPx's
/// folder model yet, so no size pill or badge is shown
/// (those would be invented numbers). The trailing
/// button still opens the delete sheet, as before.
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

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    final List<String> parts = <String>[];
    if (folder.subfolderCount > 0) {
      parts.add('${folder.subfolderCount} ${folder.subfolderCount == 1 ? "folder" : "folders"}');
    }
    if (folder.fileCount > 0) {
      parts.add('${folder.fileCount} ${folder.fileCount == 1 ? "file" : "files"}');
    }
    final String metaText = parts.isEmpty ? 'Empty' : parts.join(' • ');

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MpxRadius.lg),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          children: <Widget>[
            Container(
              width: 72,
              height: 64,
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(MpxRadius.md),
              ),
              child: Icon(
                Icons.folder_rounded,
                color: theme.mutedText,
                size: 40,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Text(
                    folder.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 16,
                      letterSpacing: -0.2,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    metaText,
                    style: TextStyle(
                      fontSize: 13,
                      color: theme.mutedText,
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
    );
  }
}
