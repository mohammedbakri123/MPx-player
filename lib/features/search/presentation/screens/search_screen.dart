import 'package:flutter/material.dart';

import '../../../../core/ui/mpx_empty_state.dart';
import '../../../../core/ui/mpx_section_header.dart';

/// Search page, presentation only.
///
/// The query, results, and recents are display props only;
/// filtering and debouncing belong to a controller later.
/// Not wired into navigation yet: HomeScreen still uses its
/// in-app-bar search mode, which this screen will replace.
class SearchScreen extends StatelessWidget {
  const SearchScreen({
    super.key,
    this.query = '',
    this.recentSearches = const <String>[],
    this.folderResults = const <String>[],
    this.fileResults = const <String>[],
    required this.onQueryChanged,
    required this.onOpenResult,
    required this.onClose,
  });

  final String query;
  final List<String> recentSearches;
  final List<String> folderResults;
  final List<String> fileResults;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onOpenResult;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final bool hasQuery = query.isNotEmpty;
    final bool hasResults =
        folderResults.isNotEmpty || fileResults.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.close_rounded),
          tooltip: 'Close search',
          onPressed: onClose,
        ),
        title: TextField(
          autofocus: true,
          decoration: const InputDecoration(
            hintText: 'Search files across library...',
            border: InputBorder.none,
          ),
          onChanged: onQueryChanged,
        ),
      ),
      body: !hasQuery
          ? _RecentSearches(
              recents: recentSearches,
              onTap: onQueryChanged,
            )
          : !hasResults
              ? MpxEmptyState(
                  icon: Icons.search_off_rounded,
                  title: 'No results',
                  message: 'No files matching "$query"',
                )
              : ListView(
                  children: <Widget>[
                    if (folderResults.isNotEmpty) ...<Widget>[
                      MpxSectionHeader(
                        title: 'FOLDERS',
                        count: folderResults.length,
                      ),
                      for (final String title in folderResults)
                        ListTile(
                          leading:
                              const Icon(Icons.folder_outlined),
                          title: Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          onTap: () => onOpenResult(title),
                        ),
                    ],
                    if (fileResults.isNotEmpty) ...<Widget>[
                      MpxSectionHeader(
                        title: 'FILES',
                        count: fileResults.length,
                      ),
                      for (final String title in fileResults)
                        ListTile(
                          leading:
                              const Icon(Icons.insert_drive_file_outlined),
                          title: Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          onTap: () => onOpenResult(title),
                        ),
                    ],
                  ],
                ),
    );
  }
}

class _RecentSearches extends StatelessWidget {
  const _RecentSearches({required this.recents, required this.onTap});

  final List<String> recents;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    if (recents.isEmpty) {
      return const MpxEmptyState(
        icon: Icons.search_rounded,
        title: 'Search your library',
        message: 'Type a file or folder name to get started.',
      );
    }

    return ListView(
      children: <Widget>[
        MpxSectionHeader(title: 'RECENT', count: recents.length),
        for (final String recent in recents)
          ListTile(
            leading: const Icon(Icons.history_rounded),
            title: Text(recent),
            onTap: () => onTap(recent),
          ),
      ],
    );
  }
}
