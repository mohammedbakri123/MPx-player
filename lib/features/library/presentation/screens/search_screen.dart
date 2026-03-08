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

  static const List<String> _suggestions = [
    'anime',
    'movie',
    'series',
    '1080p',
    'mkv',
  ];

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
    if (!mounted) return;
    setState(() {
      _favoriteIds = favorites.map((v) => v.id).toSet();
    });
  }

  Future<void> _toggleFavorite(VideoFile video) async {
    await FavoritesService.toggleFavorite(video);
    await _loadFavorites();
  }

  void _onSearchChanged(String query) {
    _debounceTimer?.cancel();
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

    if (!mounted || token != _searchToken) return;

    setState(() {
      _searchResults = results;
      _isSearching = false;
      _isIndexing = false;
    });
  }

  Future<void> _setSort(LibrarySearchSort sortBy) async {
    if (_sortBy == sortBy) return;

    setState(() {
      _sortBy = sortBy;
    });

    if (_searchQuery.trim().isEmpty) return;
    await _search(_searchQuery);
  }

  void _applySuggestion(String query) {
    _searchController.text = query;
    _searchController.selection = TextSelection.fromPosition(
      TextPosition(offset: query.length),
    );
    _search(query);
  }

  void _openVideoPlayer(VideoFile video) async {
    if (_isNavigating) return;
    setState(() => _isNavigating = true);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => VideoPlayerScreen(video: video)),
    );
    if (!mounted) return;
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
        child: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFFF8FAFC), Color(0xFFF1F5F9)],
            ),
          ),
          child: Column(
            children: [
              _buildSearchHeader(),
              _buildSortBar(),
              Expanded(child: _buildResults()),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSearchHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
          ),
          borderRadius: BorderRadius.circular(28),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFF0F172A).withValues(alpha: 0.16),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _HeaderIconButton(
                  icon: Icons.arrow_back_rounded,
                  onTap: () => Navigator.pop(context),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Search Library',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.6,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        'Find videos by title, folder, type, or quick keywords.',
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 13,
                          height: 1.35,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              height: 54,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              ),
              child: TextField(
                controller: _searchController,
                focusNode: _focusNode,
                onChanged: _onSearchChanged,
                style: const TextStyle(
                  fontSize: 15,
                  color: Colors.white,
                  fontWeight: FontWeight.w500,
                ),
                decoration: InputDecoration(
                  hintText: 'Search videos, folders, resolution...',
                  hintStyle: const TextStyle(color: Colors.white54),
                  prefixIcon:
                      const Icon(Icons.search_rounded, color: Colors.white70),
                  suffixIcon: _searchQuery.isNotEmpty
                      ? GestureDetector(
                          onTap: () {
                            _searchController.clear();
                            _search('');
                          },
                          child: const Icon(Icons.close_rounded,
                              color: Colors.white70),
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
            const SizedBox(height: 14),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: _suggestions.map((suggestion) {
                return _SuggestionChip(
                  label: suggestion,
                  onTap: () => _applySuggestion(suggestion),
                );
              }).toList(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSortBar() {
    return SizedBox(
      height: 48,
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20),
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
      return _buildStatusState(
        icon: Icons.travel_explore_rounded,
        title: _isIndexing ? 'Indexing your library' : 'Searching your library',
        subtitle: _isIndexing
            ? 'Building a faster search index for more accurate results.'
            : 'Looking through your videos and folders right now.',
        loading: true,
      );
    }

    if (_searchQuery.isEmpty) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        children: const [
          _SearchOverviewCard(
            title: 'Start with a title, folder, or quick keyword',
            subtitle:
                'Search works best with movie names, show names, formats like MKV, or quality like 1080p.',
            facts: ['Titles', 'Folders', 'Formats', 'Resolution'],
          ),
          SizedBox(height: 16),
          _SearchHintCard(
            title: 'Quick ideas',
            hints: [
              'Search by folder name if you organize by series or season',
              'Use format terms like mkv or mp4 to narrow results',
              'Try 720p or 1080p when looking for specific quality',
            ],
          ),
        ],
      );
    }

    if (_searchResults.isEmpty) {
      return _buildStatusState(
        icon: Icons.search_off_rounded,
        title: 'No results for "$_searchQuery"',
        subtitle:
            'Try shorter keywords, a folder name, or a broader search term.',
      );
    }

    final folderCount =
        _searchResults.map((video) => video.folderName).toSet().length;

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      itemCount: _searchResults.length + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: _SearchResultsHeader(
              resultCount: _searchResults.length,
              folderCount: folderCount,
              query: _searchQuery,
            ),
          );
        }

        final video = _searchResults[index - 1];
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

  Widget _buildStatusState({
    required IconData icon,
    required String title,
    required String subtitle,
    bool loading = false,
  }) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(28),
            border: Border.all(color: const Color(0xFFE2E8F0)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 24,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (loading)
                const SizedBox(
                  width: 30,
                  height: 30,
                  child: CircularProgressIndicator(strokeWidth: 2.5),
                )
              else
                Container(
                  width: 68,
                  height: 68,
                  decoration: BoxDecoration(
                    color: const Color(0xFFE2E8F0),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Icon(icon, size: 30, color: const Color(0xFF475569)),
                ),
              const SizedBox(height: 18),
              Text(
                title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF0F172A),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                subtitle,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  height: 1.45,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HeaderIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white.withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: SizedBox(
          width: 46,
          height: 46,
          child: Icon(icon, color: Colors.white),
        ),
      ),
    );
  }
}

class _SuggestionChip extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const _SuggestionChip({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchOverviewCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final List<String> facts;

  const _SearchOverviewCard({
    required this.title,
    required this.subtitle,
    required this.facts,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Search Guide',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: Color(0xFF0F766E),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            title,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: const TextStyle(
              fontSize: 14,
              height: 1.45,
              color: Color(0xFF64748B),
            ),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: facts
                .map(
                  (fact) => Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF1F5F9),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      fact,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF334155),
                      ),
                    ),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _SearchHintCard extends StatelessWidget {
  final String title;
  final List<String> hints;

  const _SearchHintCard({required this.title, required this.hints});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0F172A),
            ),
          ),
          const SizedBox(height: 12),
          ...hints.map(
            (hint) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 7,
                    height: 7,
                    margin: const EdgeInsets.only(top: 6),
                    decoration: const BoxDecoration(
                      color: Color(0xFF2563EB),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      hint,
                      style: const TextStyle(
                        fontSize: 13,
                        height: 1.4,
                        color: Color(0xFF475569),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SearchResultsHeader extends StatelessWidget {
  final int resultCount;
  final int folderCount;
  final String query;

  const _SearchResultsHeader({
    required this.resultCount,
    required this.folderCount,
    required this.query,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Search Results',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF0F766E),
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  '“$query”',
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _ResultPill(label: '$resultCount matches'),
              _ResultPill(label: '$folderCount folders'),
            ],
          ),
        ],
      ),
    );
  }
}

class _ResultPill extends StatelessWidget {
  final String label;

  const _ResultPill({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          color: Color(0xFF334155),
        ),
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
              color: isSelected
                  ? const Color(0xFF0F172A)
                  : const Color(0xFFE2E8F0),
            ),
          ),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: isSelected ? Colors.white : const Color(0xFF334155),
            ),
          ),
        ),
      ),
    );
  }
}
