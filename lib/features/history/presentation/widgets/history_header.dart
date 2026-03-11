import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class HistoryHeader extends StatelessWidget {
  final int videoCount;
  final ValueChanged<String>? onSearchChanged;
  final String searchQuery;
  final VoidCallback? onClearSearch;
  final VoidCallback? onClearHistory;

  const HistoryHeader({
    super.key,
    required this.videoCount,
    this.onSearchChanged,
    this.searchQuery = '',
    this.onClearSearch,
    this.onClearHistory,
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
              Expanded(child: _buildTitle(context)),
              if (videoCount > 0 && onClearHistory != null)
                _buildClearButton(context),
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
        color: const Color(0xFF7C3AED).withValues(
          alpha: theme.isDarkMode ? 0.18 : 0.1,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: const Icon(Icons.history, color: Color(0xFF7C3AED), size: 24),
    );
  }

  Widget _buildTitle(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'History',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: theme.strongText,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          '$videoCount ${videoCount == 1 ? 'video' : 'videos'}',
          style: TextStyle(
            fontSize: 14,
            color: theme.mutedText,
          ),
        ),
      ],
    );
  }

  Widget _buildClearButton(BuildContext context) {
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: onClearHistory,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFFDC2626).withValues(
            alpha: theme.isDarkMode ? 0.18 : 0.1,
          ),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.delete_outline, color: Colors.red.shade400, size: 16),
            const SizedBox(width: 4),
            Text(
              'Clear',
              style: TextStyle(
                color: Colors.red.shade400,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
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
          hintText: 'Search history...',
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
