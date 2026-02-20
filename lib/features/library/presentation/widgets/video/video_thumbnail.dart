import 'dart:io';
import 'package:flutter/material.dart';
import '../../../services/video_thumbnail_generator_service.dart';
import '../../../../../core/utils/cancellation_token.dart';

class VideoThumbnail extends StatefulWidget {
  final String videoPath;
  final String? existingThumbnailPath;
  final bool isFavorite;
  final ThumbnailPriority priority;

  const VideoThumbnail({
    super.key,
    required this.videoPath,
    this.existingThumbnailPath,
    this.isFavorite = false,
    this.priority = ThumbnailPriority.normal,
  });

  @override
  State<VideoThumbnail> createState() => _VideoThumbnailState();
}

class _VideoThumbnailState extends State<VideoThumbnail> {
  String? _thumbnailPath;
  bool _isLoading = false;
  CancellationToken? _cancellationToken;

  @override
  void initState() {
    super.initState();
    _loadThumbnail();
  }

  @override
  void didUpdateWidget(VideoThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPath != widget.videoPath) {
      _thumbnailPath = null;
      _isLoading = false;
      _cancellationToken?.cancel();
      _loadThumbnail();
    }
  }

  @override
  void dispose() {
    // Cancel pending thumbnail generation when widget is disposed
    _cancellationToken?.cancel();
    super.dispose();
  }

  Future<void> _loadThumbnail() async {
    // Create cancellation token for this request
    _cancellationToken = CancellationToken();

    try {
      // Use existing thumbnail if available
      if (widget.existingThumbnailPath != null) {
        final exists = await File(widget.existingThumbnailPath!).exists();
        if (exists) {
          if (mounted) {
            setState(() {
              _thumbnailPath = widget.existingThumbnailPath;
            });
          }
          return;
        }
      }

      // Check if already cached in service
      final cachedPath =
          VideoThumbnailGeneratorService().getCachedThumbnail(widget.videoPath);
      if (cachedPath != null) {
        final exists = await File(cachedPath).exists();
        if (exists) {
          if (mounted) {
            setState(() {
              _thumbnailPath = cachedPath;
            });
          }
          return;
        }
      }

      // Generate thumbnail on-demand with priority
      if (mounted) {
        setState(() {
          _isLoading = true;
        });
      }

      final thumbnailPath =
          await VideoThumbnailGeneratorService().generateThumbnail(
        widget.videoPath,
        priority: widget.priority,
        cancellationToken: _cancellationToken,
      );

      if (mounted && !_cancellationToken!.isCancelled) {
        setState(() {
          _thumbnailPath = thumbnailPath;
          _isLoading = false;
        });
      }
    } catch (e) {
      // Silently handle errors - show default icon
      if (mounted && !(_cancellationToken?.isCancelled ?? false)) {
        setState(() {
          _isLoading = false;
          _thumbnailPath = null;
        });
      }
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
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: Colors.grey,
          ),
        ),
      );
    }

    if (_thumbnailPath != null) {
      return Image.file(
        File(_thumbnailPath!),
        fit: BoxFit.cover,
        key: ValueKey(_thumbnailPath),
        frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
          if (wasSynchronouslyLoaded) return child;
          return AnimatedOpacity(
            opacity: frame == null ? 0 : 1,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
            child: child,
          );
        },
        errorBuilder: (context, error, stackTrace) {
          return const Icon(Icons.video_file, size: 32, color: Colors.grey);
        },
      );
    }

    return const Icon(Icons.video_file, size: 32, color: Colors.grey);
  }
}
