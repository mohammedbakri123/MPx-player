import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../domain/entities/video_info.dart';

class VideoPreviewCard extends StatelessWidget {
  const VideoPreviewCard({super.key, required this.videoInfo});

  final VideoInfo videoInfo;

  @override
  Widget build(BuildContext context) {
    final thumbnail =
        videoInfo.thumbnails.isNotEmpty ? videoInfo.thumbnails.first.url : null;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: thumbnail == null
                ? const ColoredBox(
                    color: Colors.black12,
                    child: Center(child: Icon(Icons.movie_creation_outlined)),
                  )
                : CachedNetworkImage(
                    imageUrl: thumbnail,
                    fit: BoxFit.cover,
                    errorWidget: (_, __, ___) => const ColoredBox(
                      color: Colors.black12,
                      child: Center(
                        child: Icon(Icons.broken_image_outlined),
                      ),
                    ),
                  ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  videoInfo.title,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                ),
                const SizedBox(height: 8),
                Text(
                  videoInfo.uploader ?? 'Unknown uploader',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                if (videoInfo.duration != null) ...[
                  const SizedBox(height: 4),
                  Text('Duration: ${videoInfo.duration} sec'),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
