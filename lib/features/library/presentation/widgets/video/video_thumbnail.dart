import 'dart:io';
import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../../../services/video_thumbnail_generator_service.dart';

class VideoThumbnail extends StatefulWidget {
  final VideoFile video;
  final bool isFavorite;

  const VideoThumbnail({
    super.key,
    required this.video,
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
    // Check if thumbnail exists
    if (widget.video.thumbnailPath != null &&
        await File(widget.video.thumbnailPath!).exists()) {
      setState(() {
        _thumbnailPath = widget.video.thumbnailPath;
      });
      return;
    }

    // Generate thumbnail
    setState(() {
      _isLoading = true;
    });

    final path =
        await VideoThumbnailService().generateThumbnail(widget.video.path);

    if (mounted) {
      setState(() {
        _thumbnailPath = path;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Stack(
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
                child:
                    const Icon(Icons.favorite, size: 12, color: Colors.white),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildThumbnailContent() {
    if (_isLoading) {
      return const Center(
        child: SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.grey),
        ),
      );
    }

    if (_thumbnailPath != null) {
      return Image.file(
        File(_thumbnailPath!),
        fit: BoxFit.cover,
        key: ValueKey(_thumbnailPath),
        cacheWidth: 150,
        cacheHeight: 105,
        errorBuilder: (context, error, stackTrace) {
          return const Icon(Icons.video_file, size: 32, color: Colors.grey);
        },
      );
    }

    return const Icon(Icons.video_file, size: 32, color: Colors.grey);
  }
}
