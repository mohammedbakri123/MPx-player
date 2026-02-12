import 'dart:io';
import 'package:flutter/material.dart';
import '../../../../../core/services/last_played_service.dart';
import '../../../../player/presentation/screens/video_player_screen.dart';
import '../../../domain/entities/video_file.dart';

class HomeFAB extends StatefulWidget {
  const HomeFAB({super.key});

  @override
  State<HomeFAB> createState() => _HomeFABState();
}

class _HomeFABState extends State<HomeFAB> {
  VideoFile? _lastVideo;

  @override
  void initState() {
    super.initState();
    _loadLastVideo();
  }

  void _loadLastVideo() {
    final video = LastPlayedService.getLastPlayedVideo();
    if (mounted && video != null) {
      setState(() {
        _lastVideo = video;
      });
    }
  }

  void _openLastVideo() {
    if (_lastVideo != null) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => VideoPlayerScreen(video: _lastVideo!),
        ),
      );
    }
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
                      child: _lastVideo!.thumbnailPath != null &&
                              File(_lastVideo!.thumbnailPath!).existsSync()
                          ? Image.file(
                              File(_lastVideo!.thumbnailPath!),
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) {
                                return const Icon(
                                  Icons.video_file,
                                  color: Colors.white70,
                                  size: 28,
                                );
                              },
                            )
                          : const Icon(
                              Icons.video_file,
                              color: Colors.white70,
                              size: 28,
                            ),
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
