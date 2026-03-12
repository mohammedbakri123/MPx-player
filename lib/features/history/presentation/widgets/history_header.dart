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
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 16),
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  theme.elevatedSurface,
                  const Color(0xFF7C3AED).withValues(
                    alpha: theme.isDarkMode ? 0.10 : 0.06,
                  ),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: theme.softBorder),
              boxShadow: [
                BoxShadow(
                  color: theme.cardShadow,
                  blurRadius: 20,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: Row(
              children: [
                _buildIcon(context),
                const SizedBox(width: 16),
                Expanded(child: _buildTitle(context)),
                if (videoCount > 0 && onClearHistory != null)
                  _buildClearButton(context),
              ],
            ),
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
        gradient: LinearGradient(
          colors: [
            const Color(0xFF7C3AED).withValues(alpha: 0.92),
            const Color(0xFFA78BFA).withValues(alpha: 0.86),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(18),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF7C3AED).withValues(
              alpha: theme.isDarkMode ? 0.24 : 0.18,
            ),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: const Icon(Icons.history_rounded, color: Colors.white, size: 24),
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
            fontWeight: FontWeight.w800,
            color: theme.strongText,
            letterSpacing: -0.8,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          videoCount == 0
              ? 'Your recent playback appears here'
              : '$videoCount recently watched ${videoCount == 1 ? 'video' : 'videos'}',
          style: TextStyle(
            fontSize: 14,
            color: theme.mutedText,
            fontWeight: FontWeight.w600,
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
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFDC2626).withValues(
            alpha: theme.isDarkMode ? 0.18 : 0.08,
          ),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: const Color(0xFFDC2626).withValues(alpha: 0.14),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.delete_outline,
                color: Color(0xFFDC2626), size: 16),
            const SizedBox(width: 4),
            const Text(
              'Clear',
              style: TextStyle(
                color: Color(0xFFDC2626),
                fontSize: 12,
                fontWeight: FontWeight.w700,
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
