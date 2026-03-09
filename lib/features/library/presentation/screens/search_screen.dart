import 'dart:async';
import 'package:flutter/material.dart';
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

  Future<void> _setSort(LibrarySearchSort sortBy) async {
    if (_sortBy == sortBy) {
      return;
    }

    setState(() {
      _sortBy = sortBy;
    });

    if (_searchQuery.trim().isEmpty) {
      return;
    }

    await _search(_searchQuery);
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
            _buildSortBar(),
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

  Widget _buildSortBar() {
    return SizedBox(
      height: 44,
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        scrollDirection: Axis.horizontal,
        children: [
          _SortChip(
            label: 'Best match',
            isSelected: _sortBy == LibrarySearchSort.relevance,
            onTap: () => _setSort(LibrarySearchSort.relevance),
          ),
          _SortChip(
            label: 'Recent',
            isSelected: _sortBy == LibrarySearchSort.recent,
            onTap: () => _setSort(LibrarySearchSort.recent),
          ),
          _SortChip(
            label: 'Name',
            isSelected: _sortBy == LibrarySearchSort.name,
            onTap: () => _setSort(LibrarySearchSort.name),
          ),
          _SortChip(
            label: 'Size',
            isSelected: _sortBy == LibrarySearchSort.size,
            onTap: () => _setSort(LibrarySearchSort.size),
          ),
        ],
      ),
    );
  }

  Widget _buildResults() {
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
                color: Colors.grey.shade600,
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

class _SortChip extends StatelessWidget {
  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  const _SortChip({
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: isSelected ? const Color(0xFF0F172A) : Colors.white,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color:
                  isSelected ? const Color(0xFF0F172A) : Colors.grey.shade200,
            ),
          ),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: isSelected ? Colors.white : const Color(0xFF334155),
            ),
          ),
        ),
      ),
    );
  }
}
