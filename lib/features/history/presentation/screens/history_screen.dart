import 'package:flutter/material.dart';
import '../../domain/entities/watch_history_entry.dart';
import '../../services/history_service.dart';
import '../widgets/history_header.dart';
import '../widgets/history_content.dart';
import '../../../player/presentation/screens/video_player_screen.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  List<WatchHistoryEntry> _entries = [];
  List<WatchHistoryEntry> _filteredEntries = [];
  String _searchQuery = '';
  bool _isLoading = true;
  bool _isNavigating = false;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    setState(() => _isLoading = true);
    try {
      final entries = await HistoryService.getHistory();
      if (mounted) {
        setState(() {
          _entries = entries;
          _applySearch();
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _applySearch() {
    if (_searchQuery.isEmpty) {
      _filteredEntries = _entries;
    } else {
      final query = _searchQuery.toLowerCase();
      _filteredEntries = _entries.where((entry) {
        final title = entry.video?.title.toLowerCase() ?? '';
        final folder = entry.video?.folderName.toLowerCase() ?? '';
        return title.contains(query) || folder.contains(query);
      }).toList();
    }
  }

  void _onSearchChanged(String query) {
    setState(() {
      _searchQuery = query;
      _applySearch();
    });
  }

  void _clearSearch() {
    setState(() {
      _searchQuery = '';
      _filteredEntries = _entries;
    });
  }

  Future<void> _openVideoPlayer(WatchHistoryEntry entry) async {
    if (_isNavigating || entry.video == null) return;
    setState(() => _isNavigating = true);

    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => VideoPlayerScreen(video: entry.video!),
      ),
    );

    setState(() => _isNavigating = false);
    _loadHistory();
  }

  Future<void> _removeFromHistory(WatchHistoryEntry entry) async {
    await HistoryService.removeFromHistory(entry.videoId);
    _loadHistory();
  }

  Future<void> _clearAllHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear History'),
        content:
            const Text('Are you sure you want to clear all watch history?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await HistoryService.clearHistory();
      _loadHistory();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            HistoryHeader(
              videoCount: _filteredEntries.length,
              searchQuery: _searchQuery,
              onSearchChanged: _onSearchChanged,
              onClearSearch: _clearSearch,
              onClearHistory: _entries.isNotEmpty ? _clearAllHistory : null,
            ),
            Expanded(
              child: HistoryContent(
                entries: _filteredEntries,
                isLoading: _isLoading,
                onRefresh: _loadHistory,
                onVideoTap: _openVideoPlayer,
                onRemove: _removeFromHistory,
                isNavigating: _isNavigating,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
