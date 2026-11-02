import 'package:flutter/material.dart';

import '../../../../core/theme/mpx_ui_tokens.dart';
import '../../models/library_sort_option.dart';

/// Sort / view dialog, MX structure in MPx's own code.
///
/// Mirrors MX's dialog sections: View Mode, Layout, Sort,
/// A-to-Z / Z-to-A, Fields, Advanced. Only the options MPx
/// actually supports are enabled: View Mode (All folders /
/// Files / Folders), Layout (List / Grid), Sort by Title
/// (name), Date, or Size, and the direction toggle.
/// Every other sort option, and Fields / Advanced, are
/// shown disabled as "Coming soon" — no fake sorting.
class MpxSortDialog extends StatelessWidget {
  const MpxSortDialog({
    super.key,
    required this.viewMode,
    required this.isGridView,
    required this.sortOption,
    required this.sortAscending,
    required this.onViewMode,
    required this.onLayout,
    required this.onSort,
    required this.onAscending,
  });

  final String viewMode;
  final bool isGridView;
  final LibrarySortOption sortOption;
  final bool sortAscending;
  final ValueChanged<String> onViewMode;
  final ValueChanged<bool> onLayout;
  final ValueChanged<LibrarySortOption> onSort;
  final ValueChanged<bool> onAscending;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Sort and view'),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const Text('View Mode',
                style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: <Widget>[
                for (final String mode in <String>['all', 'files', 'folders'])
                  ChoiceChip(
                    label: Text(
                      mode == 'all'
                          ? 'All folders'
                          : mode == 'files'
                              ? 'Files'
                              : 'Folders',
                    ),
                    selected: viewMode == mode,
                    onSelected: (_) => onViewMode(mode),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            const Text('Layout',
                style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: <Widget>[
                ChoiceChip(
                  label: const Text('List'),
                  selected: !isGridView,
                  onSelected: (_) => onLayout(false),
                ),
                ChoiceChip(
                  label: const Text('Grid'),
                  selected: isGridView,
                  onSelected: (_) => onLayout(true),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Text('Sort',
                style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: <Widget>[
                for (final LibrarySortOption option in LibrarySortOption.values)
                  ChoiceChip(
                    label: Text(option.label),
                    selected: sortOption == option,
                    onSelected: (_) => onSort(option),
                  ),
                for (final String label in <String>[
                  'Played time',
                  'Status',
                  'Length',
                  'Resolution',
                  'Path',
                  'Frame rate',
                  'Type',
                ])
                  ChoiceChip(
                    label: Text(label),
                    selected: false,
                    onSelected: null,
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: <Widget>[
                Expanded(
                  child: ChoiceChip(
                    label: const Text('A to Z'),
                    selected: sortAscending,
                    onSelected: (_) => onAscending(true),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ChoiceChip(
                    label: const Text('Z to A'),
                    selected: !sortAscending,
                    onSelected: (_) => onAscending(false),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Text('Fields',
                style: TextStyle(fontWeight: FontWeight.bold)),
            const Text('Coming soon',
                style: TextStyle(color: Colors.white70)),
            const SizedBox(height: 8),
            const Text('Advanced',
                style: TextStyle(fontWeight: FontWeight.bold)),
            const Text('Coming soon',
                style: TextStyle(color: Colors.white70)),
            const SizedBox(height: MpxSpacing.sm),
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Done'),
        ),
      ],
    );
  }
}
