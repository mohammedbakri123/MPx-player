import 'dart:io';
import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../../../services/thumbnail_cache.dart';
import '../../../services/thumbnail_worker_pool.dart';

class AnimatedThumbnail extends StatefulWidget {
  final VideoFile video;
  final double? width;
  final double? height;
  final BoxFit fit;
  final BorderRadius? borderRadius;
  final VoidCallback? onTap;
  final Widget? overlay;
  final bool showLoadingIndicator;

  const AnimatedThumbnail({
    super.key,
    required this.video,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius,
    this.onTap,
    this.overlay,
    this.showLoadingIndicator = true,
  });

  @override
  State<AnimatedThumbnail> createState() => _AnimatedThumbnailState();
}

class _AnimatedThumbnailState extends State<AnimatedThumbnail>
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
      duration: const Duration(milliseconds: 300),
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
  void didUpdateWidget(AnimatedThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.video.path != widget.video.path) {
      _fadeController.reset();
      _thumbnailPath = null;
      _hasError = false;
      _loadThumbnail();
    }
  }

  Future<void> _loadThumbnail() async {
    if (widget.video.thumbnailPath != null) {
      final file = File(widget.video.thumbnailPath!);
      if (await file.exists()) {
        if (mounted) {
          setState(() {
            _thumbnailPath = widget.video.thumbnailPath;
          });
          _fadeController.forward();
        }
        return;
      }
    }

    final cache = ThumbnailCache();
    final cachedPath = await cache.get(widget.video.path);
    if (cachedPath != null) {
      if (mounted) {
        setState(() {
          _thumbnailPath = cachedPath;
        });
        _fadeController.forward();
      }
      return;
    }

    if (mounted) {
      setState(() {
        _isLoading = true;
        _hasError = false;
      });
    }

    try {
      final workerPool = ThumbnailWorkerPool();
      final path = await workerPool.generateThumbnail(
        widget.video.path,
        smartTimestamp: true,
        durationMs: widget.video.duration > 0 ? widget.video.duration : null,
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
    return GestureDetector(
      onTap: widget.onTap,
      child: ClipRRect(
        borderRadius: widget.borderRadius ?? BorderRadius.zero,
        child: Stack(
          fit: StackFit.passthrough,
          children: [
            _buildContent(),
            if (widget.overlay != null) widget.overlay!,
          ],
        ),
      ),
    );
  }

  Widget _buildContent() {
    return SizedBox(
      width: widget.width,
      height: widget.height,
      child: Stack(
        children: [
          const ThumbnailPlaceholder(),
          if (_isLoading && widget.showLoadingIndicator)
            const Center(
              child: SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white70,
                ),
              ),
            ),
          if (_thumbnailPath != null)
            FadeTransition(
              opacity: _fadeAnimation,
              child: Image.file(
                File(_thumbnailPath!),
                fit: widget.fit,
                key: ValueKey(_thumbnailPath),
                cacheWidth: ((widget.width ?? 100) * 2).toInt(),
                cacheHeight: ((widget.height ?? 70) * 2).toInt(),
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

class ThumbnailPlaceholder extends StatelessWidget {
  final IconData? icon;

  const ThumbnailPlaceholder({super.key, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xFF424242),
            Color(0xFF212121),
            Color(0xFF303030),
          ],
          stops: [0.0, 0.5, 1.0],
        ),
      ),
      child: Center(
        child: Icon(
          icon ?? Icons.play_circle_outline,
          size: 40,
          color: Colors.white24,
        ),
      ),
    );
  }
}

class ThumbnailShimmer extends StatefulWidget {
  final Widget child;

  const ThumbnailShimmer({super.key, required this.child});

  @override
  State<ThumbnailShimmer> createState() => _ThumbnailShimmerState();
}

class _ThumbnailShimmerState extends State<ThumbnailShimmer>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    )..repeat();
    _animation = Tween<double>(begin: -2, end: 2).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOutSine),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return ShaderMask(
          shaderCallback: (bounds) {
            return LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: const [
                Colors.transparent,
                Colors.white12,
                Colors.transparent,
              ],
              stops: [
                _animation.value - 0.3,
                _animation.value,
                _animation.value + 0.3,
              ].map((e) => e.clamp(0.0, 1.0)).toList(),
            ).createShader(bounds);
          },
          blendMode: BlendMode.srcOver,
          child: widget.child,
        );
      },
      child: widget.child,
    );
  }
}
