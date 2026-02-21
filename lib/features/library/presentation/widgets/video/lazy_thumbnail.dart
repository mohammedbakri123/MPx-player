import 'dart:io';
import 'package:flutter/rendering.dart';
import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../../../services/thumbnail_cache.dart';
import '../../../services/thumbnail_worker_pool.dart';

class LazyThumbnail extends StatefulWidget {
  final VideoFile video;
  final Widget placeholder;
  final Widget? loadingIndicator;
  final double? width;
  final double? height;
  final BoxFit fit;
  final BorderRadius? borderRadius;
  final VoidCallback? onTap;

  const LazyThumbnail({
    super.key,
    required this.video,
    required this.placeholder,
    this.loadingIndicator,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius,
    this.onTap,
  });

  @override
  State<LazyThumbnail> createState() => _LazyThumbnailState();
}

class _LazyThumbnailState extends State<LazyThumbnail> {
  String? _thumbnailPath;
  bool _isLoading = false;
  bool _hasLoaded = false;

  @override
  void initState() {
    super.initState();
    _checkExistingThumbnail();
  }

  @override
  void didUpdateWidget(LazyThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.video.path != widget.video.path) {
      _hasLoaded = false;
      _thumbnailPath = null;
      _checkExistingThumbnail();
    }
  }

  Future<void> _checkExistingThumbnail() async {
    if (widget.video.thumbnailPath != null) {
      final file = File(widget.video.thumbnailPath!);
      if (await file.exists()) {
        if (mounted) {
          setState(() {
            _thumbnailPath = widget.video.thumbnailPath;
            _hasLoaded = true;
          });
        }
        return;
      }
    }

    final cache = ThumbnailCache();
    final cachedPath = await cache.get(widget.video.path);
    if (cachedPath != null && mounted) {
      setState(() {
        _thumbnailPath = cachedPath;
        _hasLoaded = true;
      });
    }
  }

  Future<void> _loadThumbnail() async {
    if (_hasLoaded || _isLoading) return;

    setState(() {
      _isLoading = true;
    });

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
          _hasLoaded = true;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  void _onVisibilityChanged(bool isVisible) {
    if (isVisible && !_hasLoaded && !_isLoading) {
      _loadThumbnail();
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onTap,
      child: ClipRRect(
        borderRadius: widget.borderRadius ?? BorderRadius.zero,
        child: VisibilityDetector(
          onVisibilityChanged: _onVisibilityChanged,
          child: SizedBox(
            width: widget.width,
            height: widget.height,
            child: _buildContent(),
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (_isLoading) {
      return widget.loadingIndicator ?? widget.placeholder;
    }

    if (_thumbnailPath != null) {
      return Image.file(
        File(_thumbnailPath!),
        fit: widget.fit,
        key: ValueKey(_thumbnailPath),
        cacheWidth: ((widget.width ?? 100) * 2).toInt(),
        cacheHeight: ((widget.height ?? 70) * 2).toInt(),
        filterQuality: FilterQuality.medium,
        errorBuilder: (context, error, stackTrace) {
          return widget.placeholder;
        },
      );
    }

    return widget.placeholder;
  }
}

class VisibilityDetector extends StatefulWidget {
  final Widget child;
  final void Function(bool isVisible) onVisibilityChanged;

  const VisibilityDetector({
    super.key,
    required this.child,
    required this.onVisibilityChanged,
  });

  @override
  State<VisibilityDetector> createState() => _VisibilityDetectorState();
}

class _VisibilityDetectorState extends State<VisibilityDetector> {
  bool _wasVisible = false;
  final GlobalKey _key = GlobalKey();

  void _checkVisibility() {
    if (!mounted) return;

    final renderObject = _key.currentContext?.findRenderObject() as RenderBox?;
    if (renderObject == null || !renderObject.hasSize) return;

    final viewport = RenderAbstractViewport.of(renderObject);
    final offsetToReveal = viewport.getOffsetToReveal(renderObject, 0.0);
    final viewportDimension = viewport.paintBounds.size.height;

    final itemTop = offsetToReveal.offset;
    final itemBottom = itemTop + renderObject.size.height;
    const viewportTop = 0.0;
    final viewportBottom = viewportDimension;

    final isVisible = itemBottom > viewportTop && itemTop < viewportBottom;

    if (isVisible != _wasVisible) {
      _wasVisible = isVisible;
      widget.onVisibilityChanged(isVisible);
    }
  }

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollUpdateNotification) {
          _checkVisibility();
        }
        return false;
      },
      child: Opacity(
        key: _key,
        opacity: 1.0,
        child: widget.child,
      ),
    );
  }
}
