import 'dart:io';
import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../library/services/thumbnail_cache.dart';
import '../../../library/services/thumbnail_worker_pool.dart';
import '../../domain/entities/watch_history_entry.dart';

class HistoryThumbnail extends StatefulWidget {
  final WatchHistoryEntry entry;

  const HistoryThumbnail({super.key, required this.entry});

  @override
  State<HistoryThumbnail> createState() => _HistoryThumbnailState();
}

class _HistoryThumbnailState extends State<HistoryThumbnail> {
  String? _thumbnailPath;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _thumbnailPath = widget.entry.video?.thumbnailPath;
    _loadThumbnail();
  }

  @override
  void didUpdateWidget(covariant HistoryThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.entry.video?.path != widget.entry.video?.path) {
      _thumbnailPath = widget.entry.video?.thumbnailPath;
      _loadThumbnail();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: _buildThumbnailContent(),
      ),
    );
  }

  Widget _buildThumbnailContent() {
    if (_thumbnailPath != null) {
      final file = File(_thumbnailPath!);
      if (file.existsSync()) {
        return Image.file(
          file,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => _buildPlaceholder(),
        );
      }
    }

    if (_isLoading) {
      return _buildLoadingState();
    }

    return _buildPlaceholder();
  }

  Future<void> _loadThumbnail() async {
    final video = widget.entry.video;
    if (video == null) {
      return;
    }

    if (_thumbnailPath != null && File(_thumbnailPath!).existsSync()) {
      return;
    }

    final cache = ThumbnailCache();
    final cachedPath = await cache.get(video.path);
    if (!mounted) return;

    if (cachedPath != null && File(cachedPath).existsSync()) {
      setState(() {
        _thumbnailPath = cachedPath;
      });
      return;
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final generatedPath = await ThumbnailWorkerPool()
          .generateThumbnail(video.path)
          .timeout(const Duration(seconds: 10), onTimeout: () => null);
      if (!mounted) return;
      setState(() {
        _thumbnailPath = generatedPath;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
    }
  }

  Widget _buildLoadingState() {
    return Container(
      color: Colors.black.withValues(alpha: 0.12),
      child: const Center(
        child: SizedBox(
          width: 26,
          height: 26,
          child: CircularProgressIndicator(strokeWidth: 2.2),
        ),
      ),
    );
  }

  Widget _buildPlaceholder() {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            theme.subtleSurface,
            theme.colorScheme.primary.withValues(alpha: 0.08),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.play_circle_outline,
              size: 48,
              color: theme.faintText,
            ),
            const SizedBox(height: 8),
            Text(
              widget.entry.video?.resolution ?? '',
              style: TextStyle(
                color: theme.mutedText,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
