import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import 'favorites_title.dart';

class FavoritesHeader extends StatelessWidget {
  final int videoCount;
  final ValueChanged<String>? onSearchChanged;
  final String searchQuery;
  final VoidCallback? onClearSearch;

  const FavoritesHeader({
    super.key,
    required this.videoCount,
    this.onSearchChanged,
    this.searchQuery = '',
    this.onClearSearch,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 16),
      child: Column(
        children: [
          Row(
            children: [
              _buildIcon(context),
              const SizedBox(width: 16),
              Expanded(child: FavoritesTitle(videoCount: videoCount)),
            ],
          ),
          const SizedBox(height: 16),
          _buildSearchField(context),
        ],
      ),
    );
  }

  Widget _buildIcon(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: const Color(0xFFE11D48).withValues(
          alpha: theme.isDarkMode ? 0.18 : 0.1,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: const Icon(Icons.favorite, color: Color(0xFFE11D48), size: 24),
    );
  }

  Widget _buildSearchField(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
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
        onChanged: onSearchChanged,
        style: TextStyle(fontSize: 16, color: theme.strongText),
        decoration: InputDecoration(
          hintText: 'Search favorites...',
          hintStyle: TextStyle(color: theme.faintText),
          prefixIcon: Icon(Icons.search, color: theme.faintText),
          suffixIcon: searchQuery.isNotEmpty
              ? GestureDetector(
                  onTap: onClearSearch,
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
    );
  }
}
