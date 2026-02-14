import 'dart:io';
import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../../../core/services/video_thumbnail_generator_service.dart';
import 'video_thumbnail_placeholder.dart';
import 'video_thumbnail_overlay.dart';

class VideoThumbnail extends StatefulWidget {
  final VideoFile video;

  const VideoThumbnail({super.key, required this.video});

  @override
  State<VideoThumbnail> createState() => _VideoThumbnailState();
}

class _VideoThumbnailState extends State<VideoThumbnail> {
  String? _thumbnailPath;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadThumbnail();
  }

  Future<void> _loadThumbnail() async {
    // Use existing thumbnail if available
    if (widget.video.thumbnailPath != null &&
        File(widget.video.thumbnailPath!).existsSync()) {
      setState(() {
        _thumbnailPath = widget.video.thumbnailPath;
      });
      return;
    }

    // Generate thumbnail on-demand
    setState(() {
      _isLoading = true;
    });

    final thumbnailPath = await VideoThumbnailGeneratorService()
        .generateThumbnail(widget.video.path);

    if (mounted) {
      setState(() {
        _thumbnailPath = thumbnailPath;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Stack(
          fit: StackFit.expand,
          children: [
            _buildThumbnailContent(),
            VideoThumbnailBadge(
                text: widget.video.formattedSize, isTop: false, isLeft: false),
            const VideoPlayOverlay(),
          ],
        ),
      ),
    );
  }

  Widget _buildThumbnailContent() {
    if (_isLoading) {
      return Container(
        color: Colors.grey.shade200,
        child: const Center(
          child: SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: Colors.grey,
            ),
          ),
        ),
      );
    }

    if (_thumbnailPath != null && File(_thumbnailPath!).existsSync()) {
      return Image.file(
        File(_thumbnailPath!),
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) {
          return const VideoThumbnailPlaceholder();
        },
      );
    }

    return const VideoThumbnailPlaceholder();
  }
}
