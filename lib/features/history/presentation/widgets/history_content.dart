import 'package:flutter/material.dart';
import '../../domain/entities/watch_history_entry.dart';
import 'history_empty_state.dart';
import 'history_loading_state.dart';
import 'history_list.dart';

class HistoryContent extends StatelessWidget {
  final List<WatchHistoryEntry> entries;
  final bool isLoading;
  final Future<void> Function() onRefresh;
  final void Function(WatchHistoryEntry) onVideoTap;
  final void Function(WatchHistoryEntry) onRemove;
  final bool isNavigating;
  final VoidCallback? onBrowseVideos;

  const HistoryContent({
    super.key,
    required this.entries,
    required this.isLoading,
    required this.onRefresh,
    required this.onVideoTap,
    required this.onRemove,
    required this.isNavigating,
    this.onBrowseVideos,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const HistoryLoadingState();
    }
    if (entries.isEmpty) {
      return HistoryEmptyState(onBrowseVideos: onBrowseVideos);
    }
    return HistoryList(
      entries: entries,
      onRefresh: onRefresh,
      onVideoTap: onVideoTap,
      onRemove: onRemove,
      isNavigating: isNavigating,
    );
  }
}
