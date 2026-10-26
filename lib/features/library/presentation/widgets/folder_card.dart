import 'package:flutter/material.dart';
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

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: scheme.outlineVariant.withValues(alpha: 0.5),
        ),
      ),
      color: scheme.surfaceContainerLow,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: <Widget>[
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: scheme.primaryContainer.withValues(alpha: 0.7),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  Icons.folder_rounded,
                  color: scheme.onPrimaryContainer,
                  size: 26,
                ),
              ),
              const SizedBox(width: 12),
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
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _buildSubtitle(),
                      style: TextStyle(
                        fontSize: 12,
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
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
                          Text('Delete folder', style: TextStyle(color: Colors.redAccent)),
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
    );
  }

  String _buildSubtitle() {
    final List<String> parts = <String>[];
    if (folder.subfolderCount > 0) {
      parts.add('${folder.subfolderCount} ${folder.subfolderCount == 1 ? 'folder' : 'folders'}');
    }
    if (folder.fileCount > 0) {
      parts.add('${folder.fileCount} ${folder.fileCount == 1 ? 'file' : 'files'}');
    }
    if (parts.isEmpty) {
      return 'Empty';
    }
    return parts.join(', ');
  }
}
