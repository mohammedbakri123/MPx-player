import 'package:flutter/material.dart';
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
              _buildIcon(),
              const SizedBox(width: 16),
              Expanded(child: FavoritesTitle(videoCount: videoCount)),
            ],
          ),
          const SizedBox(height: 16),
          _buildSearchField(),
        ],
      ),
    );
  }

  Widget _buildIcon() {
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Icon(Icons.favorite, color: Colors.red.shade500, size: 24),
    );
  }

  Widget _buildSearchField() {
    return Container(
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
        onChanged: onSearchChanged,
        style: const TextStyle(fontSize: 16),
        decoration: InputDecoration(
          hintText: 'Search favorites...',
          hintStyle: TextStyle(color: Colors.grey.shade400),
          prefixIcon: Icon(Icons.search, color: Colors.grey.shade400),
          suffixIcon: searchQuery.isNotEmpty
              ? GestureDetector(
                  onTap: onClearSearch,
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
    );
  }
}
