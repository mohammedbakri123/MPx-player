import 'package:flutter/material.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import '../../data/datasources/local_video_scanner.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../../favorites/services/favorites_service.dart';
import '../widgets/video/folder_detail_header.dart';
import '../widgets/video/empty_video_state.dart';
import '../widgets/video/video_list.dart';

class FolderDetailScreen extends StatefulWidget {
  final VideoFolder folder;

  const FolderDetailScreen({super.key, required this.folder});

  @override
  State<FolderDetailScreen> createState() => _FolderDetailScreenState();
}

class _FolderDetailScreenState extends State<FolderDetailScreen> {
  List<VideoFile> _videos = [];
  Set<String> _favoriteIds = {};
  bool _isLoading = true;
  bool _isNavigating = false;

  @override
  void initState() {
    super.initState();
    _loadVideos();
    _loadFavorites();
  }

  Future<void> _loadVideos() async {
    final videos = await VideoScanner().getVideosInFolder(widget.folder.path);
    setState(() {
      _videos = videos;
      _isLoading = false;
    });
  }

  Future<void> _loadFavorites() async {
    final favorites = await FavoritesService.getFavorites();
    setState(() {
      _favoriteIds = favorites.map((v) => v.id).toSet();
    });
  }

  Future<void> _toggleFavorite(VideoFile video) async {
    await FavoritesService.toggleFavorite(video);
    _loadFavorites();
  }

  void _openVideoPlayer(VideoFile video) async {
    if (_isNavigating) return;
    setState(() => _isNavigating = true);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => VideoPlayerScreen(video: video)),
    );
    setState(() => _isNavigating = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            FolderDetailHeader(
                folder: widget.folder,
                onBackPressed: () => Navigator.pop(context)),
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _videos.isEmpty
                      ? const EmptyVideoState()
                      : VideoList(
                          videos: _videos,
                          onRefresh: _loadVideos,
                          onVideoTap: _openVideoPlayer,
                          onAddToFavorites: _toggleFavorite,
                          favoriteIds: _favoriteIds,
                          isNavigating: _isNavigating,
                        ),
            ),
          ],
        ),
      ),
    );
  }
}
