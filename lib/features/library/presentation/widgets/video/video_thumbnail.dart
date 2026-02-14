import 'dart:io';
import 'package:flutter/material.dart';
import '../../../../../core/services/video_thumbnail_generator_service.dart';

class VideoThumbnail extends StatefulWidget {
  final String videoPath;
  final String? existingThumbnailPath;
  final bool isFavorite;

  const VideoThumbnail({
    super.key,
    required this.videoPath,
    this.existingThumbnailPath,
    this.isFavorite = false,
  });

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
    if (widget.existingThumbnailPath != null &&
        File(widget.existingThumbnailPath!).existsSync()) {
      setState(() {
        _thumbnailPath = widget.existingThumbnailPath;
      });
      return;
    }

    // Generate thumbnail on-demand
    setState(() {
      _isLoading = true;
    });

    final thumbnailPath = await VideoThumbnailGeneratorService()
        .generateThumbnail(widget.videoPath);

    if (mounted) {
      setState(() {
        _thumbnailPath = thumbnailPath;
        _isLoading = false;
      });
    }
  }

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
            child: _buildThumbnailContent(),
          ),
        ),
        if (widget.isFavorite)
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

  Widget _buildThumbnailContent() {
    if (_isLoading) {
      return const Center(
        child: SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: Colors.grey,
          ),
        ),
      );
    }

    if (_thumbnailPath != null && File(_thumbnailPath!).existsSync()) {
      return Image.file(
        File(_thumbnailPath!),
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) {
          return const Icon(Icons.video_file, size: 32, color: Colors.grey);
        },
      );
    }

    return const Icon(Icons.video_file, size: 32, color: Colors.grey);
  }
}
