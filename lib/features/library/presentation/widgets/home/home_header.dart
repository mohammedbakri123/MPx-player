import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback? onSortTap;
  final VoidCallback? onSearchTap;
  final VoidCallback? onToggleViewTap;
  final VoidCallback? onAddReelTap;
  final VoidCallback? onPlayFolderAsReelsTap; // New callback
  final bool isGridView;

  const HomeHeader({
    super.key,
    this.onSortTap,
    this.onSearchTap,
    this.onToggleViewTap,
    this.onAddReelTap,
    this.onPlayFolderAsReelsTap, // Initialize
    this.isGridView = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          Text(
            'MPx',
            style: TextStyle(
              color: theme.strongText,
              fontSize: 22,
              fontWeight: FontWeight.w800,
            ),
          ),
          const Spacer(),
          IconButton(
            icon: Icon(
                isGridView ? Icons.view_list_rounded : Icons.grid_view_rounded),
            onPressed: onToggleViewTap,
            tooltip: isGridView ? 'List View' : 'Grid View',
          ),
          IconButton(
            icon: const Icon(Icons.sort_rounded),
            onPressed: onSortTap,
            tooltip: 'Sort',
          ),
          // IconButton(
          //   icon: const Icon(Icons.video_collection_rounded),
          //   onPressed: onAddReelTap,
          //   tooltip: 'Add current folder to Reels',
          // ),
          IconButton(
            icon: const Icon(Icons.play_circle_outline_rounded),
            onPressed: onPlayFolderAsReelsTap,
            tooltip: 'Play current folder as Reels',
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
