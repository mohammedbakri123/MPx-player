import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../data/repositories/favorites_repository.dart';
import '../widgets/favorites_header.dart';
import '../widgets/favorites_content.dart';
import '../../../player/presentation/screens/video_player_screen.dart';

class FavoritesScreen extends StatefulWidget {
  const FavoritesScreen({super.key});
  @override
  State<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends State<FavoritesScreen> {
  List<VideoFile> _videos = [];
  bool _isLoading = true, _isScanning = false, _isNavigating = false;

  @override
  void initState() {
    super.initState();
    _loadAllVideos();
  }

  Future<void> _loadAllVideos() async {
    if (_isScanning) return;
    setState(() => _isLoading = _isScanning = true);
    try {
      final videos = await FavoritesRepository.loadVideos();
      if (mounted)
        setState(() {
          _videos = videos;
          _isLoading = _isScanning = false;
        });
    } catch (e) {
      if (mounted) setState(() => _isLoading = _isScanning = false);
    }
  }

  Future<void> _openVideoPlayer(VideoFile video) async {
    if (_isNavigating) return;
    setState(() => _isNavigating = true);
    await Navigator.push(context,
        MaterialPageRoute(builder: (c) => VideoPlayerScreen(video: video)));
    setState(() => _isNavigating = false);
  }

  void _loadDemoData() =>
      setState(() => _videos = FavoritesRepository.loadDemoVideos());

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        body: SafeArea(
            child: Column(children: [
          FavoritesHeader(videoCount: _videos.length, onFilterTap: () {}),
          Expanded(
              child: FavoritesContent(
                  videos: _videos,
                  isLoading: _isLoading,
                  onRefresh: _loadAllVideos,
                  onVideoTap: _openVideoPlayer,
                  isNavigating: _isNavigating,
                  onTryDemo: _loadDemoData)),
        ])),
      );
}
