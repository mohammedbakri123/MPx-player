import 'dart:io';
import 'package:flutter/material.dart';

class VideoThumbnail extends StatelessWidget {
  final String? thumbnailPath;
  final bool isFavorite;

  const VideoThumbnail({
    super.key,
    this.thumbnailPath,
    this.isFavorite = false,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        ClipRRect(
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
                      return const Icon(Icons.video_file,
                          size: 32, color: Colors.grey);
                    },
                  )
                : const Icon(Icons.video_file, size: 32, color: Colors.grey),
          ),
        ),
        if (isFavorite)
          Positioned(
            top: 4,
            right: 4,
            child: Container(
              padding: const EdgeInsets.all(2),
              decoration: const BoxDecoration(
                color: Colors.red,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.favorite, size: 12, color: Colors.white),
            ),
          ),
      ],
    );
  }
}
