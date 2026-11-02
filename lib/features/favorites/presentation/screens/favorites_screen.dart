import 'package:flutter/material.dart';

import '../../../../core/ui/mpx_empty_state.dart';
import '../../../../core/ui/mpx_section_header.dart';

/// Favourites page, presentation only.
///
/// [favoriteTitles] is a display prop only: the real favourites
/// list lives in a controller later. Until then, an empty list
/// shows the shared empty state, and a non-empty list shows the
/// same rows as the Library list view (titles only, for now).
/// Not wired into navigation yet.
class FavoritesScreen extends StatelessWidget {
  const FavoritesScreen({
    super.key,
    this.favoriteTitles = const <String>[],
    required this.onOpenTitle,
  });

  final List<String> favoriteTitles;
  final ValueChanged<String> onOpenTitle;

  @override
  Widget build(BuildContext context) {
    if (favoriteTitles.isEmpty) {
      return const Scaffold(
        appBar: _FavoritesAppBar(),
        body: MpxEmptyState(
          icon: Icons.star_border_rounded,
          title: 'No favourites yet',
          message:
              'Star a file from its details sheet to see it here.',
        ),
      );
    }

    return Scaffold(
      appBar: const _FavoritesAppBar(),
      body: ListView(
        children: <Widget>[
          MpxSectionHeader(
            title: 'FAVOURITES',
            count: favoriteTitles.length,
          ),
          for (final String title in favoriteTitles)
            ListTile(
              leading: const Icon(Icons.star_rounded),
              title: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              onTap: () => onOpenTitle(title),
            ),
        ],
      ),
    );
  }
}

class _FavoritesAppBar extends StatelessWidget
    implements PreferredSizeWidget {
  const _FavoritesAppBar();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return AppBar(title: const Text('Favourites'));
  }
}
