import 'dart:io';
import 'package:flutter/material.dart';
import '../../domain/entities/watch_history_entry.dart';

class HistoryThumbnail extends StatelessWidget {
  final WatchHistoryEntry entry;

  const HistoryThumbnail({super.key, required this.entry});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: _buildThumbnailContent(),
      ),
    );
  }

  Widget _buildThumbnailContent() {
    if (entry.video?.thumbnailPath != null) {
      final file = File(entry.video!.thumbnailPath!);
      if (file.existsSync()) {
        return Image.file(
          file,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => _buildPlaceholder(),
        );
      }
    }

    return _buildPlaceholder();
  }

  Widget _buildPlaceholder() {
    return Container(
      color: Colors.grey.shade200,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.play_circle_outline,
                size: 48, color: Colors.grey.shade400),
            const SizedBox(height: 8),
            Text(
              entry.video?.resolution ?? '',
              style: TextStyle(
                color: Colors.grey.shade500,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
