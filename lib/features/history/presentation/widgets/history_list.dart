import 'package:flutter/material.dart';
import '../../domain/entities/watch_history_entry.dart';
import 'history_list_item.dart';

class HistoryList extends StatelessWidget {
  final List<WatchHistoryEntry> entries;
  final Future<void> Function() onRefresh;
  final void Function(WatchHistoryEntry) onVideoTap;
  final void Function(WatchHistoryEntry) onRemove;
  final bool isNavigating;

  const HistoryList({
    super.key,
    required this.entries,
    required this.onRefresh,
    required this.onVideoTap,
    required this.onRemove,
    required this.isNavigating,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      color: Colors.purple.shade500,
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        itemCount: entries.length,
        itemBuilder: (context, index) {
          final entry = entries[index];
          return HistoryListItem(
            entry: entry,
            onTap: isNavigating ? null : () => onVideoTap(entry),
            onRemove: () => onRemove(entry),
            isLoading: isNavigating,
          );
        },
      ),
    );
  }
}
