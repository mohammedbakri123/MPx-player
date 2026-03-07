import 'dart:io';
import 'dart:async';
import 'package:flutter/material.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/file_item.dart';
import '../../data/datasources/directory_browser.dart';
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
  final DirectoryBrowser _browser = DirectoryBrowser();
  Set<String> _favoriteIds = {};
  List<VideoFile> _searchResults = [];
  bool _isNavigating = false;
  bool _isSearching = false;
  String _searchQuery = '';
  Timer? _debounceTimer;

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

  void _onSearchChanged(String query) {
    if (_debounceTimer?.isActive ?? false) _debounceTimer!.cancel();

    _debounceTimer = Timer(const Duration(milliseconds: 500), () {
      _search(query);
    });
  }

  Future<void> _search(String query) async {
    final trimmed = query.trim().toLowerCase();
    setState(() {
      _searchQuery = trimmed;
    });

    if (trimmed.isEmpty) {
      setState(() {
        _searchResults = [];
        _isSearching = false;
      });
      return;
    }

    setState(() => _isSearching = true);

    final rootPath = _browser.getRootPath();
    final results = <VideoFile>[];
    await _searchDirectory(rootPath, trimmed, results, depth: 0);

    if (mounted) {
      setState(() {
        _searchResults = results;
        _isSearching = false;
      });
    }
  }

  Future<void> _searchDirectory(
    String path,
    String query,
    List<VideoFile> results, {
    required int depth,
  }) async {
    if (depth > 5) return;

    // Skip system folders that are massive and don't contain user videos
    if (path.endsWith('/Android') || path.contains('/Android/')) return;

    try {
      final dir = Directory(path);
      if (!await dir.exists()) return;

      // Yield to event loop to keep UI smooth during deep traversal
      await Future.delayed(Duration.zero);

      await for (final entity in dir.list(followLinks: false)) {
        if (entity is File) {
          final name = entity.path.split('/').last;
          if (name.toLowerCase().contains(query)) {
            final item = FileItem(
              path: entity.path,
              name: name,
              isDirectory: false,
              size: (await entity.stat()).size,
              modified: (await entity.stat()).modified,
            );
            if (item.isVideo) {
              results.add(VideoFile.fromFileItem(item, path));
            }
          }
        } else if (entity is Directory) {
          final name = entity.path.split('/').last;
          if (!name.startsWith('.') && depth < 5) {
            await _searchDirectory(entity.path, query, results,
                depth: depth + 1);
          }
        }
      }
    } catch (e) {
      // Skip folders without permission
    }
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
    _debounceTimer?.cancel();
    _searchController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            _buildSearchBar(),
            Expanded(
              child: _buildResults(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
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
                onChanged: _onSearchChanged,
                style: const TextStyle(fontSize: 16),
                decoration: InputDecoration(
                  hintText: 'Search videos...',
                  hintStyle: TextStyle(color: Colors.grey.shade400),
                  prefixIcon: Icon(Icons.search, color: Colors.grey.shade400),
                  suffixIcon: _searchQuery.isNotEmpty
                      ? GestureDetector(
                          onTap: () {
                            _searchController.clear();
                            _search('');
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

  Widget _buildResults() {
    if (_isSearching) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_searchQuery.isEmpty) {
      return _buildEmptyState(
          'Search for videos', 'Enter a search term to find videos');
    }

    if (_searchResults.isEmpty) {
      return _buildEmptyState(
        'No results found',
        'Try searching with different keywords',
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: _searchResults.length,
      itemBuilder: (context, index) {
        final video = _searchResults[index];
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
