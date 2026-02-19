import 'dart:io';
import 'package:flutter/material.dart';
import 'package:mpx/features/library/services/video_thumbnail_generator_service.dart';
import '../../../../player/services/last_played_service.dart';
// import '../../../library/services/video_thumbnail_generator_service.dart';
import '../../../../player/presentation/screens/video_player_screen.dart';
import '../../../domain/entities/video_file.dart';

class HomeFAB extends StatefulWidget {
  const HomeFAB({super.key});

  @override
  State<HomeFAB> createState() => _HomeFABState();
}

class _HomeFABState extends State<HomeFAB> {
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
    // Reload when widget becomes visible again (e.g., returning from video player)
    // This gets called when we navigate back from another screen
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadLastVideo();
    });
  }

  void _loadLastVideo() {
    final video = LastPlayedService.getLastPlayedVideo();
    if (mounted) {
      setState(() {
        _lastVideo = video;
        // Reset thumbnail if video changed
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

    // Use existing thumbnail if available
    if (_lastVideo!.thumbnailPath != null &&
        File(_lastVideo!.thumbnailPath!).existsSync()) {
      setState(() {
        _thumbnailPath = _lastVideo!.thumbnailPath;
      });
      return;
    }

    // Generate thumbnail on-demand
    setState(() {
      _isLoadingThumbnail = true;
    });

    final thumbnailPath = await VideoThumbnailGeneratorService()
        .generateThumbnail(_lastVideo!.path);

    if (mounted) {
      setState(() {
        _thumbnailPath = thumbnailPath;
        _isLoadingThumbnail = false;
      });
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
      // Reload the last video after returning from player
      _loadLastVideo();
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
    final hasLastVideo = _lastVideo != null;

    return GestureDetector(
      onTap: hasLastVideo ? _openLastVideo : null,
      child: Container(
        width: hasLastVideo ? 200 : 64,
        height: 64,
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFF6366F1).withValues(alpha: 0.4),
              blurRadius: 20,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: hasLastVideo
            ? Row(
                children: [
                  // Thumbnail
                  ClipRRect(
                    borderRadius: const BorderRadius.horizontal(
                      left: Radius.circular(20),
                    ),
                    child: Container(
                      width: 64,
                      height: 64,
                      color: Colors.black26,
                      child: _buildThumbnail(),
                    ),
                  ),
                  // Title and play icon
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
                                const Text(
                                  'Continue Watching',
                                  style: TextStyle(
                                    color: Colors.white70,
                                    fontSize: 10,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _lastVideo!.title,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          const Icon(
                            Icons.play_arrow,
                            color: Colors.white,
                            size: 28,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              )
            : const Center(
                child: Icon(
                  Icons.play_arrow,
                  color: Colors.white,
                  size: 28,
                ),
              ),
      ),
    );
  }
}
