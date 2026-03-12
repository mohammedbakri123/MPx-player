import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../library/domain/entities/video_file.dart';
import 'video_thumbnail.dart';
import 'video_info.dart';

class VideoCard extends StatelessWidget {
  final VideoFile video;
  final VoidCallback? onTap;
  final VoidCallback? onRemove;
  final bool isLoading;

  const VideoCard({
    super.key,
    required this.video,
    this.onTap,
    this.onRemove,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: Colors.transparent,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: InkWell(
          onTap: isLoading ? null : onTap,
          borderRadius: BorderRadius.circular(24),
          child: Ink(
            decoration: BoxDecoration(
              color: theme.elevatedSurface,
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: theme.cardShadow,
                  blurRadius: 22,
                  offset: const Offset(0, 6),
                ),
              ],
              border: Border.all(color: theme.softBorder),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Stack(
                  children: [
                    VideoThumbnail(video: video),
                    Positioned(
                      top: 10,
                      left: 10,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.5),
                          borderRadius: BorderRadius.circular(999),
                        ),
                        child: const Text(
                          'Favorite',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ),
                    if (onRemove != null)
                      Positioned(
                        top: 10,
                        right: 10,
                        child: Material(
                          color:
                              const Color(0xFFDC2626).withValues(alpha: 0.92),
                          borderRadius: BorderRadius.circular(12),
                          child: InkWell(
                            onTap: isLoading ? null : onRemove,
                            borderRadius: BorderRadius.circular(12),
                            child: const Padding(
                              padding: EdgeInsets.all(8),
                              child: Icon(
                                Icons.delete_outline,
                                color: Colors.white,
                                size: 18,
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                VideoInfo(video: video),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
