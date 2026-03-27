import 'package:flutter/material.dart';

import '../../domain/entities/download_item.dart';
import '../../domain/enums/download_status.dart';

class DownloadProgressTile extends StatelessWidget {
  const DownloadProgressTile({
    super.key,
    required this.item,
    this.onPause,
    this.onResume,
    this.onCancel,
    this.onPlay,
    this.onDelete,
  });

  final DownloadItem item;
  final VoidCallback? onPause;
  final VoidCallback? onResume;
  final VoidCallback? onCancel;
  final VoidCallback? onPlay;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final isComplete = item.status == DownloadStatus.completed;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              item.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: 8),
            Text(item.status.name),
            const SizedBox(height: 10),
            LinearProgressIndicator(value: isComplete ? 1 : item.progress),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              children: [
                if (!isComplete && item.status == DownloadStatus.downloading)
                  OutlinedButton.icon(
                    onPressed: onPause,
                    icon: const Icon(Icons.pause_rounded),
                    label: const Text('Pause'),
                  ),
                if (!isComplete && item.status == DownloadStatus.paused)
                  OutlinedButton.icon(
                    onPressed: onResume,
                    icon: const Icon(Icons.play_arrow_rounded),
                    label: const Text('Resume'),
                  ),
                if (!isComplete)
                  TextButton.icon(
                    onPressed: onCancel,
                    icon: const Icon(Icons.close_rounded),
                    label: const Text('Cancel'),
                  ),
                if (isComplete && item.savePath != null)
                  FilledButton.icon(
                    onPressed: onPlay,
                    icon: const Icon(Icons.play_circle_fill_rounded),
                    label: const Text('Play'),
                  ),
                if (isComplete)
                  TextButton.icon(
                    onPressed: onDelete,
                    icon: const Icon(Icons.delete_outline_rounded),
                    label: const Text('Delete'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
