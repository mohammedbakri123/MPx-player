import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

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
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Favorites',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: theme.strongText,
              letterSpacing: -0.8,
            ),
          ),
          if (videoCount > 0) ...[
            const SizedBox(height: 2),
            Text(
              '$videoCount saved videos',
              style: TextStyle(
                fontSize: 13,
                color: theme.mutedText,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
          const SizedBox(height: 12),
          _buildSearchField(context),
        ],
      ),
    );
  }

  Widget _buildSearchField(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      height: 44,
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: theme.softBorder),
      ),
      child: TextField(
        onChanged: onSearchChanged,
        style: TextStyle(fontSize: 14, color: theme.strongText),
        decoration: InputDecoration(
          hintText: 'Search favorites...',
          hintStyle: TextStyle(color: theme.faintText, fontSize: 14),
          prefixIcon: Icon(Icons.search, color: theme.faintText, size: 20),
          suffixIcon: searchQuery.isNotEmpty
              ? GestureDetector(
                  onTap: onClearSearch,
                  child: Icon(Icons.clear, color: theme.faintText, size: 18),
                )
              : null,
          border: InputBorder.none,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        ),
      ),
    );
  }
}
