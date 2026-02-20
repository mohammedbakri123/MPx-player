import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../services/favorites_service.dart';
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
  List<VideoFile> _filteredVideos = [];
  String _searchQuery = '';
  bool _isLoading = true, _isNavigating = false;

  @override
  void initState() {
    super.initState();
    _loadFavorites();
  }

  Future<void> _loadFavorites() async {
    setState(() => _isLoading = true);
    try {
      final videos = await FavoritesService.getFavorites();
      if (mounted) {
        setState(() {
          _videos = videos;
          _applySearch();
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _applySearch() {
    if (_searchQuery.isEmpty) {
      _filteredVideos = _videos;
    } else {
      final query = _searchQuery.toLowerCase();
      _filteredVideos = _videos.where((video) {
        return video.title.toLowerCase().contains(query) ||
            video.folderName.toLowerCase().contains(query);
      }).toList();
    }
  }

  void _onSearchChanged(String query) {
    setState(() {
      _searchQuery = query;
      _applySearch();
    });
  }

  void _clearSearch() {
    setState(() {
      _searchQuery = '';
      _filteredVideos = _videos;
    });
  }

  Future<void> _openVideoPlayer(VideoFile video) async {
    if (_isNavigating) return;
    setState(() => _isNavigating = true);
    await Navigator.push(context,
        MaterialPageRoute(builder: (c) => VideoPlayerScreen(video: video)));
    setState(() => _isNavigating = false);
  }

  Future<void> _removeFromFavorites(VideoFile video) async {
    await FavoritesService.toggleFavorite(video);
    _loadFavorites();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        body: SafeArea(
            child: Column(children: [
          FavoritesHeader(
            videoCount: _filteredVideos.length,
            searchQuery: _searchQuery,
            onSearchChanged: _onSearchChanged,
            onClearSearch: _clearSearch,
          ),
          Expanded(
              child: FavoritesContent(
                  videos: _filteredVideos,
                  isLoading: _isLoading,
                  onRefresh: _loadFavorites,
                  onVideoTap: _openVideoPlayer,
                  isNavigating: _isNavigating,
                  onRemove: _removeFromFavorites,
                  onTryDemo: null)),
        ])),
      );
}
