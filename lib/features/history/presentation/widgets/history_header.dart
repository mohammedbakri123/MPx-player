import 'package:flutter/material.dart';

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
              _buildIcon(),
              const SizedBox(width: 16),
              Expanded(child: _buildTitle()),
              if (videoCount > 0 && onClearHistory != null) _buildClearButton(),
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
        color: Colors.purple.shade50,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Icon(Icons.history, color: Colors.purple.shade500, size: 24),
    );
  }

  Widget _buildTitle() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'History',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1E293B),
          ),
        ),
        const SizedBox(height: 2),
        Text(
          '$videoCount ${videoCount == 1 ? 'video' : 'videos'}',
          style: TextStyle(
            fontSize: 14,
            color: Colors.grey.shade500,
          ),
        ),
      ],
    );
  }

  Widget _buildClearButton() {
    return GestureDetector(
      onTap: onClearHistory,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.red.shade50,
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
          hintText: 'Search history...',
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
