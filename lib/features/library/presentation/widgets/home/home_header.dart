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
    return PopupMenuButton<LibraryViewMode>(
      tooltip: 'Change view',
      initialValue: viewMode,
      onSelected: onChanged,
      itemBuilder: (context) => [
        PopupMenuItem(
          value: LibraryViewMode.list,
          child: _ViewModeItem(
            icon: Icons.view_list_rounded,
            label: 'List',
            isSelected: viewMode == LibraryViewMode.list,
          ),
        ),
        PopupMenuItem(
          value: LibraryViewMode.grid,
          child: _ViewModeItem(
            icon: Icons.grid_view_rounded,
            label: 'Grid',
            isSelected: viewMode == LibraryViewMode.grid,
          ),
        ),
      ],
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Icon(
          _iconFor(viewMode),
          color: theme.colorScheme.primary,
        ),
      ),
    );
  }

  IconData _iconFor(LibraryViewMode mode) {
    switch (mode) {
      case LibraryViewMode.list:
        return Icons.view_list_rounded;
      case LibraryViewMode.grid:
        return Icons.grid_view_rounded;
    }
  }
}

class _ViewModeItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isSelected;

  const _ViewModeItem({
    required this.icon,
    required this.label,
    required this.isSelected,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(
          icon,
          size: 20,
          color: isSelected ? theme.colorScheme.primary : theme.mutedText,
        ),
        const SizedBox(width: 12),
        Text(
          label,
          style: TextStyle(
            fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
            color: isSelected ? theme.colorScheme.primary : theme.strongText,
          ),
        ),
        if (isSelected) ...[
          const SizedBox(width: 8),
          Icon(
            Icons.check_rounded,
            size: 16,
            color: theme.colorScheme.primary,
          ),
        ],
      ],
    );
  }
}
