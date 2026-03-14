import 'dart:io';
import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../services/thumbnail_worker_pool.dart';
import '../../../services/thumbnail_cache.dart';
import '../../../../history/services/history_service.dart';
import '../../../../player/presentation/screens/video_player_screen.dart';
import '../../../domain/entities/video_file.dart';

class HomeFAB extends StatefulWidget {
  static final RouteObserver<PageRoute> routeObserver =
      RouteObserver<PageRoute>();

  const HomeFAB({super.key});

  @override
  State<HomeFAB> createState() => HomeFABState();
}

class HomeFABState extends State<HomeFAB> with RouteAware {
  VideoFile? _lastVideo;
  String? _thumbnailPath;
  bool _isLoadingThumbnail = false;

  @override
  void initState() {
    super.initState();
    _loadLastVideo();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    HomeFAB.routeObserver.subscribe(this, ModalRoute.of(context) as PageRoute);
  }

  @override
  void dispose() {
    HomeFAB.routeObserver.unsubscribe(this);
    super.dispose();
  }

  @override
  void didPopNext() {
    _loadLastVideo();
  }

  Future<void> _loadLastVideo() async {
    final video = await HistoryService.getLastPlayedVideo();
    if (mounted) {
      setState(() {
        _lastVideo = video;
        if (_lastVideo?.id != video?.id) {
          _thumbnailPath = null;
        }
      });
      if (video != null) {
        _loadThumbnail();
      }
    }
  }

  Future<void> _loadThumbnail() async {
    if (_lastVideo == null) return;

    final cache = ThumbnailCache();
    final cachedPath = await cache.get(_lastVideo!.path);
    if (cachedPath != null) {
      if (mounted) {
        setState(() {
          _thumbnailPath = cachedPath;
          _isLoadingThumbnail = false;
        });
      }
      return;
    }

    if (_lastVideo!.thumbnailPath != null &&
        File(_lastVideo!.thumbnailPath!).existsSync()) {
      setState(() {
        _thumbnailPath = _lastVideo!.thumbnailPath;
      });
      return;
    }

    setState(() {
      _isLoadingThumbnail = true;
    });

    try {
      final thumbnailPath = await ThumbnailWorkerPool()
          .generateThumbnail(_lastVideo!.path)
          .timeout(
            const Duration(seconds: 10),
            onTimeout: () => null,
          );

      if (mounted) {
        setState(() {
          _thumbnailPath = thumbnailPath;
          _isLoadingThumbnail = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoadingThumbnail = false;
        });
      }
    }
  }

  void _openLastVideo() async {
    if (_lastVideo != null) {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => VideoPlayerScreen(video: _lastVideo!),
        ),
      );
      await _loadLastVideo();
    }
  }

  Widget _buildThumbnail() {
    if (_isLoadingThumbnail) {
      return const Center(
        child: SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: Colors.white70,
          ),
        ),
      );
    }

    if (_thumbnailPath != null && File(_thumbnailPath!).existsSync()) {
      return Image.file(
        File(_thumbnailPath!),
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) {
          return const Icon(
            Icons.video_file,
            color: Colors.white70,
            size: 28,
          );
        },
      );
    }

    return const Icon(
      Icons.video_file,
      color: Colors.white70,
      size: 28,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasLastVideo = _lastVideo != null;

    return Material(
      color: Colors.transparent,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 75),
        child: InkWell(
          onTap: hasLastVideo ? _openLastVideo : null,
          borderRadius: BorderRadius.circular(24),
          child: Ink(
            width: hasLastVideo ? 232 : 68,
            height: 68,
            decoration: BoxDecoration(
              color: theme.elevatedSurface,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: theme.softBorder),
              boxShadow: [
                BoxShadow(
                  color: theme.cardShadow,
                  blurRadius: 24,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: hasLastVideo
                ? Row(
                    children: [
                      ClipRRect(
                        borderRadius: const BorderRadius.horizontal(
                          left: Radius.circular(24),
                        ),
                        child: Container(
                          width: 72,
                          height: 68,
                          color: Colors.black.withValues(alpha: 0.18),
                          child: _buildThumbnail(),
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'Continue watching',
                                      style: TextStyle(
                                        color: theme.mutedText,
                                        fontSize: 10,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    const SizedBox(height: 3),
                                    Text(
                                      _lastVideo!.title,
                                      style: TextStyle(
                                        color: theme.strongText,
                                        fontSize: 12,
                                        fontWeight: FontWeight.w800,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                              Container(
                                width: 34,
                                height: 34,
                                decoration: BoxDecoration(
                                  color: theme.colorScheme.primary.withValues(
                                    alpha: 0.14,
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Icon(
                                  Icons.play_arrow_rounded,
                                  color: theme.colorScheme.primary,
                                  size: 22,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  )
                : Center(
                    child: Container(
                      width: 38,
                      height: 38,
                      decoration: BoxDecoration(
                        color:
                            theme.colorScheme.primary.withValues(alpha: 0.14),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Icon(
                        Icons.play_arrow_rounded,
                        color: theme.colorScheme.primary,
                        size: 24,
                      ),
                    ),
                  ),
          ),
        ),
      ),
    );
  }
}
