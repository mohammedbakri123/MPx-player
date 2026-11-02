import 'package:flutter/material.dart';

import '../../../../core/ui/mpx_empty_state.dart';
import '../../../../core/ui/mpx_section_header.dart';

/// History / Recently Played page, presentation only.
///
/// Groups are display props only: Today, Yesterday, Earlier.
/// The real resume positions and ordering live in a controller
/// later. Not wired into navigation yet.
class HistoryScreen extends StatelessWidget {
  const HistoryScreen({
    super.key,
    this.today = const <String>[],
    this.yesterday = const <String>[],
    this.earlier = const <String>[],
    required this.onOpenTitle,
  });

  final List<String> today;
  final List<String> yesterday;
  final List<String> earlier;
  final ValueChanged<String> onOpenTitle;

  bool get _isEmpty =>
      today.isEmpty && yesterday.isEmpty && earlier.isEmpty;

  @override
  Widget build(BuildContext context) {
    if (_isEmpty) {
      return const Scaffold(
        appBar: _HistoryAppBar(),
        body: MpxEmptyState(
          icon: Icons.history_rounded,
          title: 'No history yet',
          message: 'Files you play will appear here.',
        ),
      );
    }

    return Scaffold(
      appBar: const _HistoryAppBar(),
      body: ListView(
        children: <Widget>[
          if (today.isNotEmpty) ...<Widget>[
            MpxSectionHeader(title: 'TODAY', count: today.length),
            for (final String title in today)
              _HistoryRow(title: title, onTap: onOpenTitle),
          ],
          if (yesterday.isNotEmpty) ...<Widget>[
            MpxSectionHeader(
              title: 'YESTERDAY',
              count: yesterday.length,
            ),
            for (final String title in yesterday)
              _HistoryRow(title: title, onTap: onOpenTitle),
          ],
          if (earlier.isNotEmpty) ...<Widget>[
            MpxSectionHeader(title: 'EARLIER', count: earlier.length),
            for (final String title in earlier)
              _HistoryRow(title: title, onTap: onOpenTitle),
          ],
        ],
      ),
    );
  }
}

class _HistoryRow extends StatelessWidget {
  const _HistoryRow({required this.title, required this.onTap});

  final String title;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: const Icon(Icons.play_circle_outline_rounded),
      title: Text(
        title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      onTap: () => onTap(title),
    );
  }
}

class _HistoryAppBar extends StatelessWidget
    implements PreferredSizeWidget {
  const _HistoryAppBar();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return AppBar(title: const Text('History'));
  }
}
