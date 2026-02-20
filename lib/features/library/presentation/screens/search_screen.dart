import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controller/library_controller.dart';
import '../../domain/entities/video_file.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../../favorites/services/favorites_service.dart';
import '../widgets/video/video_list_item.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  Set<String> _favoriteIds = {};
  bool _isNavigating = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
      _loadFavorites();
    });
  }

  Future<void> _loadFavorites() async {
    final favorites = await FavoritesService.getFavorites();
    if (mounted) {
      setState(() {
        _favoriteIds = favorites.map((v) => v.id).toSet();
      });
    }
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
  void dispose() {
    _searchController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<LibraryController>();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            _buildSearchBar(controller),
            Expanded(
              child: _buildResults(controller),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSearchBar(LibraryController controller) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          GestureDetector(
            onTap: () {
              controller.clearSearch();
              Navigator.pop(context);
            },
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: const Icon(Icons.arrow_back, color: Colors.grey),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Container(
              height: 50,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade200),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 10,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: TextField(
                controller: _searchController,
                focusNode: _focusNode,
                onChanged: (value) => controller.search(value),
                style: const TextStyle(fontSize: 16),
                decoration: InputDecoration(
                  hintText: 'Search videos...',
                  hintStyle: TextStyle(color: Colors.grey.shade400),
                  prefixIcon: Icon(Icons.search, color: Colors.grey.shade400),
                  suffixIcon: controller.searchQuery.isNotEmpty
                      ? GestureDetector(
                          onTap: () {
                            _searchController.clear();
                            controller.clearSearch();
                          },
                          child: Icon(Icons.clear, color: Colors.grey.shade400),
                        )
                      : null,
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 15,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildResults(LibraryController controller) {
    if (!controller.isSearching) {
      return _buildEmptyState(
          'Search for videos', 'Enter a search term to find videos');
    }

    if (controller.searchResults.isEmpty) {
      return _buildEmptyState(
        'No results found',
        'Try searching with different keywords',
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: controller.searchResults.length,
      itemBuilder: (context, index) {
        final video = controller.searchResults[index];
        return VideoListItem(
          video: video,
          onTap: () => _openVideoPlayer(video),
          onAddToFavorites: () => _toggleFavorite(video),
          isFavorite: _favoriteIds.contains(video.id),
          isLoading: _isNavigating,
        );
      },
    );
  }

  Widget _buildEmptyState(String title, String subtitle) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.search,
            size: 64,
            color: Colors.grey.shade300,
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.grey.shade600,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey.shade400,
            ),
          ),
        ],
      ),
    );
  }
}
