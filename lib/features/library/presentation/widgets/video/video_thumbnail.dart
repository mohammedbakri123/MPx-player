import 'dart:io';
import 'package:flutter/material.dart';

class VideoThumbnail extends StatelessWidget {
  final String? thumbnailPath;

  const VideoThumbnail({
    super.key,
    this.thumbnailPath,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 100,
        height: 70,
        color: Colors.grey.shade200,
        child: thumbnailPath != null && File(thumbnailPath!).existsSync()
            ? Image.file(
                File(thumbnailPath!),
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) {
                  return const Icon(
                    Icons.video_file,
                    size: 32,
                    color: Colors.grey,
                  );
                },
              )
            : const Icon(
                Icons.video_file,
                size: 32,
                color: Colors.grey,
              ),
      ),
    );
  }
}
