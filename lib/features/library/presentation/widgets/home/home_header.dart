import 'package:flutter/material.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback? onSortTap;
  final VoidCallback? onSearchTap;
  final VoidCallback? onToggleViewTap;
  final bool isGridView;

  const HomeHeader({
    super.key,
    this.onSortTap,
    this.onSearchTap,
    this.onToggleViewTap,
    this.isGridView = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          const Text(
            'MPx',
            style: TextStyle(
              color: Color(0xFF0F172A),
              fontSize: 22,
              fontWeight: FontWeight.w800,
            ),
          ),
          const Spacer(),
          IconButton(
            icon: Icon(isGridView ? Icons.view_list_rounded : Icons.grid_view_rounded),
            onPressed: onToggleViewTap,
            tooltip: isGridView ? 'List View' : 'Grid View',
          ),
          IconButton(
            icon: const Icon(Icons.sort_rounded),
            onPressed: onSortTap,
            tooltip: 'Sort',
          ),
          IconButton(
            icon: const Icon(Icons.search_rounded),
            onPressed: onSearchTap,
            tooltip: 'Search',
          ),
        ],
      ),
    );
  }
}
