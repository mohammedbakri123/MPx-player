import 'dart:io';
import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../../../services/thumbnail_cache.dart';
import '../../../services/thumbnail_worker_pool.dart';

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

class _VideoThumbnailState extends State<VideoThumbnail>
    with SingleTickerProviderStateMixin {
  String? _thumbnailPath;
  bool _isLoading = false;
  bool _hasError = false;
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 250),
      vsync: this,
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeOut,
    );
    _loadThumbnail();
  }

  @override
  void dispose() {
    _fadeController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(VideoThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.video.path != widget.video.path) {
      _fadeController.reset();
      _thumbnailPath = null;
      _hasError = false;
      _loadThumbnail();
    }
  }

  Future<void> _loadThumbnail() async {
    final cache = ThumbnailCache();

    final cachedPath = await cache.get(widget.video.path);
    if (cachedPath != null) {
      if (mounted) {
        setState(() {
          _thumbnailPath = cachedPath;
          _isLoading = false;
          _hasError = false;
        });
        _fadeController.forward();
      }
      return;
    }

    if (widget.video.thumbnailPath != null) {
      final file = File(widget.video.thumbnailPath!);
      if (await file.exists()) {
        if (mounted) {
          setState(() {
            _thumbnailPath = widget.video.thumbnailPath;
            _isLoading = false;
            _hasError = false;
          });
          _fadeController.forward();
        }
        return;
      }
    }

    if (mounted) {
      setState(() {
        _isLoading = true;
        _hasError = false;
      });
    }

    try {
      final workerPool = ThumbnailWorkerPool();
      final path = await workerPool
          .generateThumbnail(
            widget.video.path,
            smartTimestamp: true,
            durationMs:
                widget.video.duration > 0 ? widget.video.duration : null,
          )
          .timeout(
            const Duration(seconds: 10),
            onTimeout: () => null,
          );

      if (mounted) {
        setState(() {
          _thumbnailPath = path;
          _isLoading = false;
          _hasError = path == null;
        });
        if (path != null) {
          _fadeController.forward();
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
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
    return Stack(
      children: [
        Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF424242), Color(0xFF212121)],
            ),
          ),
          child: const Center(
            child: Icon(Icons.play_circle_outline,
                size: 28, color: Colors.white24),
          ),
        ),
        if (_isLoading)
          Container(
            color: Colors.black26,
            child: const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white70,
                ),
              ),
            ),
          ),
        if (_thumbnailPath != null)
          FadeTransition(
            opacity: _fadeAnimation,
            child: Image.file(
              File(_thumbnailPath!),
              fit: BoxFit.cover,
              key: ValueKey(_thumbnailPath),
              cacheWidth: 200,
              cacheHeight: 140,
              filterQuality: FilterQuality.medium,
              errorBuilder: (context, error, stackTrace) {
                return const SizedBox.shrink();
              },
            ),
          ),
      ],
    );
  }
}
