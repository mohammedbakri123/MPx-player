import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme_tokens.dart';
import '../../domain/entities/video_file.dart';
import '../../data/datasources/directory_browser.dart';
import '../../services/library_index_service.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../../favorites/services/favorites_service.dart';
import '../widgets/video/video_thumbnail.dart';
import '../widgets/common/library_item_details_sheet.dart';

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
            CircularProgressIndicator(color: theme.colorScheme.primary),
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

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          child: Row(
            children: [
              Text(
                '${_searchResults.length} result${_searchResults.length == 1 ? '' : 's'}',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: theme.mutedText,
                ),
              ),
              const Spacer(),
              _SortDropdown(
                value: _sortBy,
                onChanged: (sort) {
                  setState(() => _sortBy = sort);
                  _search(_searchQuery);
                },
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            itemCount: _searchResults.length,
            itemBuilder: (context, index) {
              final video = _searchResults[index];
              return _SearchResultItem(
                video: video,
                query: _searchQuery,
                onTap: () => _openVideoPlayer(video),
                onAddToFavorites: () => _toggleFavorite(video),
                isFavorite: _favoriteIds.contains(video.id),
                isLoading: _isNavigating,
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildEmptyState(String title, String subtitle) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.search_off_rounded,
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
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: theme.mutedText,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SortDropdown extends StatelessWidget {
  final LibrarySearchSort value;
  final ValueChanged<LibrarySearchSort> onChanged;

  const _SortDropdown({
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.softBorder),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<LibrarySearchSort>(
          value: value,
          icon: Icon(Icons.sort, size: 18, color: theme.mutedText),
          dropdownColor: theme.elevatedSurface,
          style: TextStyle(fontSize: 13, color: theme.strongText),
          items: const [
            DropdownMenuItem(
              value: LibrarySearchSort.relevance,
              child: Text('Relevance'),
            ),
            DropdownMenuItem(
              value: LibrarySearchSort.recent,
              child: Text('Recent'),
            ),
            DropdownMenuItem(
              value: LibrarySearchSort.name,
              child: Text('Name'),
            ),
            DropdownMenuItem(
              value: LibrarySearchSort.size,
              child: Text('Size'),
            ),
          ],
          onChanged: (v) {
            if (v != null) onChanged(v);
          },
        ),
      ),
    );
  }
}

class _SearchResultItem extends StatelessWidget {
  final VideoFile video;
  final String query;
  final VoidCallback onTap;
  final VoidCallback onAddToFavorites;
  final bool isLoading;
  final bool isFavorite;

  const _SearchResultItem({
    required this.video,
    required this.query,
    required this.onTap,
    required this.onAddToFavorites,
    required this.isLoading,
    required this.isFavorite,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      onLongPress: () => _showContextMenu(context),
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: theme.elevatedSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: theme.softBorder),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: 72,
              height: 72,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: VideoThumbnail(
                  video: video,
                  isFavorite: isFavorite,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    video.folderName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: theme.colorScheme.primary,
                      letterSpacing: 0.2,
                    ),
                  ),
                  const SizedBox(height: 2),
                  _HighlightText(
                    text: video.title,
                    query: query,
                    maxLines: 2,
                  ),
                  const SizedBox(height: 4),
                  _CompactMetadata(video: video),
                ],
              ),
            ),
            const SizedBox(width: 8),
            GestureDetector(
              onTap: () => _showContextMenu(context),
              child: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: theme.subtleSurface,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  Icons.more_horiz,
                  color: theme.mutedText,
                  size: 16,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showContextMenu(BuildContext context) {
    LibraryItemDetailsSheet.showForVideo(
      context,
      video,
      isFavorite: isFavorite,
      onToggleFavorite: onAddToFavorites,
    );
  }
}

class _HighlightText extends StatelessWidget {
  final String text;
  final String query;
  final int maxLines;

  const _HighlightText({
    required this.text,
    required this.query,
    required this.maxLines,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lowerText = text.toLowerCase();
    final lowerQuery = query.toLowerCase();
    final queryIndex = lowerText.indexOf(lowerQuery);

    if (queryIndex == -1) {
      return Text(
        text,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: theme.strongText,
          height: 1.2,
        ),
        maxLines: maxLines,
        overflow: TextOverflow.ellipsis,
      );
    }

    final before = text.substring(0, queryIndex);
    final match = text.substring(queryIndex, queryIndex + query.length);
    final after = text.substring(queryIndex + query.length);

    return RichText(
      maxLines: maxLines,
      overflow: TextOverflow.ellipsis,
      text: TextSpan(
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: theme.strongText,
          height: 1.2,
        ),
        children: [
          TextSpan(text: before),
          TextSpan(
            text: match,
            style: TextStyle(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w700,
            ),
          ),
          TextSpan(text: after),
        ],
      ),
    );
  }
}

class _CompactMetadata extends StatelessWidget {
  final VideoFile video;

  const _CompactMetadata({required this.video});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final resolution = video.resolution;
    final hasResolution = resolution != 'Unknown';

    return Wrap(
      spacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (hasResolution)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
            decoration: BoxDecoration(
              color: theme.colorScheme.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(5),
            ),
            child: Text(
              resolution,
              style: TextStyle(
                fontSize: 9,
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.primary,
              ),
            ),
          ),
        Text(
          video.formattedSize,
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: theme.mutedText,
          ),
        ),
        if (video.duration > 0)
          Text(
            '• ${video.formattedDuration}',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: theme.mutedText,
            ),
          ),
      ],
    );
  }
}
