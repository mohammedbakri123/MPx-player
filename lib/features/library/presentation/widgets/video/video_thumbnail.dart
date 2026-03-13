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
      _loadThumbnail();
    }
  }

  Future<void> _loadThumbnail() async {
    final cache = ThumbnailCache();

    final memoryData = cache.memoryCache.get(widget.video.path);
    if (memoryData != null) {
      if (mounted) {
        setState(() {
          _thumbnailPath = null;
          _isLoading = false;
        });
        _fadeController.forward(from: 0);
      }
      return;
    }

    final cachedPath = await cache.get(widget.video.path);
    if (cachedPath != null && await File(cachedPath).exists()) {
      if (mounted) {
        setState(() {
          _thumbnailPath = cachedPath;
          _isLoading = false;
        });
        _fadeController.forward(from: 0);
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
          });
          _fadeController.forward(from: 0);
        }
        return;
      }
    }

    if (mounted) {
      setState(() {
        _isLoading = true;
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
        });
        if (path != null) {
          _fadeController.forward(from: 0);
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          _buildThumbnailContent(),
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
    final cache = ThumbnailCache();
    final memoryData = cache.memoryCache.get(widget.video.path);

    return Container(
      color: Colors.transparent,
      child: Stack(
        fit: StackFit.expand,
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
          if (_isLoading && memoryData == null)
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
          if (memoryData != null)
            FadeTransition(
              opacity: _fadeAnimation,
              child: Image.memory(
                memoryData,
                fit: BoxFit.cover,
                key: ValueKey('mem_${widget.video.path}'),
                cacheWidth: 200,
                cacheHeight: 140,
                filterQuality: FilterQuality.medium,
                errorBuilder: (context, error, stackTrace) {
                  return const SizedBox.shrink();
                },
              ),
            )
          else if (_thumbnailPath != null && File(_thumbnailPath!).existsSync())
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
      ),
    );
  }
}
