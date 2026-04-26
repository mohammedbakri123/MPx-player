import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';
import '../../../domain/enums/library_view_mode.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback? onSortTap;
  final VoidCallback? onSearchTap;
  final ValueChanged<LibraryViewMode>? onViewModeChanged;
  final VoidCallback? onAddReelTap;
  final VoidCallback? onPlayFolderAsReelsTap;
  final LibraryViewMode viewMode;

  const HomeHeader({
    super.key,
    this.onSortTap,
    this.onSearchTap,
    this.onViewModeChanged,
    this.onAddReelTap,
    this.onPlayFolderAsReelsTap,
    this.viewMode = LibraryViewMode.list,
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
          _ViewModeSelector(
            viewMode: viewMode,
            onChanged: onViewModeChanged,
          ),
          IconButton(
            icon: const Icon(Icons.sort_rounded),
            onPressed: onSortTap,
            tooltip: 'Sort',
          ),
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

class _ViewModeSelector extends StatelessWidget {
  final LibraryViewMode viewMode;
  final ValueChanged<LibraryViewMode>? onChanged;

  const _ViewModeSelector({
    required this.viewMode,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.subtleSurface,
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: () {
          if (onChanged != null) {
            final nextMode = viewMode == LibraryViewMode.list
                ? LibraryViewMode.grid
                : LibraryViewMode.list;
            onChanged!(nextMode);
          }
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.view_list_rounded,
                size: 18,
                color: viewMode == LibraryViewMode.list
                    ? theme.colorScheme.primary
                    : theme.mutedText,
              ),
              const SizedBox(width: 4),
              Container(
                width: 1,
                height: 18,
                color: theme.softBorder,
              ),
              const SizedBox(width: 4),
              Icon(
                Icons.grid_view_rounded,
                size: 18,
                color: viewMode == LibraryViewMode.grid
                    ? theme.colorScheme.primary
                    : theme.mutedText,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
