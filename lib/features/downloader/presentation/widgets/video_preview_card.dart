import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

import '../../domain/entities/video_info.dart';

class VideoPreviewCard extends StatelessWidget {
  const VideoPreviewCard({super.key, required this.videoInfo});

  final VideoInfo videoInfo;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final thumbnail =
        videoInfo.thumbnails.isNotEmpty ? videoInfo.thumbnails.first.url : null;

    return Container(
      decoration: BoxDecoration(
        color: theme.cardTheme.color ?? theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: theme.cardShadow,
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: thumbnail == null
                ? ColoredBox(
                    color: theme.colorScheme.surfaceContainerHighest
                        .withValues(alpha: 0.3),
                    child: Center(
                      child: Icon(
                        Icons.movie_creation_outlined,
                        size: 48,
                        color: theme.colorScheme.outline,
                      ),
                    ),
                  )
                : Stack(
                    fit: StackFit.expand,
                    children: [
                      CachedNetworkImage(
                        imageUrl: thumbnail,
                        fit: BoxFit.cover,
                        errorWidget: (_, __, ___) => ColoredBox(
                          color: theme.colorScheme.surfaceContainerHighest
                              .withValues(alpha: 0.3),
                          child: Center(
                            child: Icon(
                              Icons.broken_image_outlined,
                              size: 48,
                              color: theme.colorScheme.outline,
                            ),
                          ),
                        ),
                      ),
                      Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.transparent,
                              Colors.black.withValues(alpha: 0.4),
                            ],
                          ),
                        ),
                      ),
                      if (videoInfo.duration != null)
                        Positioned(
                          bottom: 8,
                          right: 8,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.7),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              _formatDuration(videoInfo.duration!),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  videoInfo.title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    height: 1.3,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    CircleAvatar(
                      radius: 14,
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Icon(
                        Icons.account_circle_rounded,
                        size: 18,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        videoInfo.uploader ?? 'Unknown uploader',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.mutedText,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (videoInfo.viewCount != null)
                      Row(
                        children: [
                          Icon(
                            Icons.visibility_outlined,
                            size: 14,
                            color: theme.faintText,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            _formatViewCount(videoInfo.viewCount!),
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.faintText,
                            ),
                          ),
                        ],
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatDuration(int seconds) {
    final minutes = seconds ~/ 60;
    final secs = seconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }

  String _formatViewCount(int count) {
    if (count >= 1000000000) {
      return '${(count / 1000000000).toStringAsFixed(1)}B';
    }
    if (count >= 1000000) {
      return '${(count / 1000000).toStringAsFixed(1)}M';
    }
    if (count >= 1000) {
      return '${(count / 1000).toStringAsFixed(1)}K';
    }
    return count.toString();
  }
}
