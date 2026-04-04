import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme_tokens.dart';
import '../../domain/entities/video_file.dart';
import '../../data/datasources/directory_browser.dart';
import '../../services/library_index_service.dart';
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
  final LibraryIndexService _indexService = LibraryIndexService();
  Set<String> _favoriteIds = {};
  List<VideoFile> _searchResults = [];
  bool _isNavigating = false;
  bool _isSearching = false;
  bool _isIndexing = false;
  String _searchQuery = '';
  Timer? _debounceTimer;
  int _searchToken = 0;
  LibrarySearchSort _sortBy = LibrarySearchSort.relevance;

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

    _debounceTimer = Timer(const Duration(milliseconds: 250), () {
      _search(query);
    });
  }

  Future<void> _search(String query) async {
    final trimmed = query.trim();
    final normalized = trimmed.toLowerCase();
    final token = ++_searchToken;

    setState(() {
      _searchQuery = trimmed;
    });

    if (normalized.isEmpty) {
      setState(() {
        _searchResults = [];
        _isSearching = false;
        _isIndexing = false;
      });
      return;
    }

    setState(() {
      _isSearching = true;
      _isIndexing = _indexService.getSnapshot(_browser.getRootPath()) == null;
    });

    final rootPath = _browser.getRootPath();
    final results = await _indexService.search(
      rootPath,
      normalized,
      sortBy: _sortBy,
    );

    if (mounted && token == _searchToken) {
      setState(() {
        _searchResults = results;
        _isSearching = false;
        _isIndexing = false;
      });
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
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.appBackground,
      resizeToAvoidBottomInset: false,
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
    final theme = Theme.of(context);
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
                color: theme.elevatedSurface,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: theme.softBorder),
              ),
              child: Icon(Icons.arrow_back, color: theme.mutedText),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Container(
              height: 50,
              decoration: BoxDecoration(
                color: theme.elevatedSurface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: theme.softBorder),
                boxShadow: [
                  BoxShadow(
                    color: theme.cardShadow,
                    blurRadius: 10,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: TextField(
                controller: _searchController,
                focusNode: _focusNode,
                onChanged: _onSearchChanged,
                style: TextStyle(fontSize: 16, color: theme.strongText),
                decoration: InputDecoration(
                  hintText: 'Search videos...',
                  hintStyle: TextStyle(color: theme.faintText),
                  prefixIcon: Icon(Icons.search, color: theme.faintText),
                  suffixIcon: _searchQuery.isNotEmpty
                      ? GestureDetector(
                          onTap: () {
                            _searchController.clear();
                            _search('');
                          },
                          child: Icon(Icons.clear, color: theme.faintText),
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
    final theme = Theme.of(context);
    if (_isSearching) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              _isIndexing ? 'Indexing your library...' : 'Searching videos...',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: theme.mutedText,
              ),
            ),
          ],
        ),
      );
    }

    if (_searchQuery.isEmpty) {
      return _buildEmptyState(
        'Search your library',
        'Try a title, folder name, or a few keywords',
      );
    }

    if (_searchResults.isEmpty) {
      return _buildEmptyState(
        'No results found',
        'Try shorter keywords or search by folder name',
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
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.search,
            size: 64,
            color: theme.faintText,
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: theme.strongText,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 14,
              color: theme.mutedText,
            ),
          ),
        ],
      ),
    );
  }
}
