import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import '../domain/entities/file_item.dart';
import '../data/datasources/directory_browser.dart';
import '../services/library_index_service.dart';

enum SortBy { name, date, size, videos }

enum SortOrder { ascending, descending }

class FileBrowserController extends ChangeNotifier {
  static String? _persistedCurrentPath;
  static List<String> _persistedPathHistory = <String>[];

  final DirectoryBrowser _browser = DirectoryBrowser();
  final LibraryIndexService _indexService = LibraryIndexService();

  List<FileItem> _items = [];
  final List<String> _pathHistory = [];
  String _currentPath = '';
  bool _isLoading = false;
  bool _isInitialized = false;
  bool _showOnlyVideos = true; // Default to TRUE as requested
  bool _isGridView = false;
  SortBy _sortBy = SortBy.name;
  SortOrder _sortOrder = SortOrder.ascending;
  String? _error;
  final Set<String> _selectedItems = {};
  bool _isSelectionMode = false;

  List<FileItem> get items => _items;
  String get currentPath => _currentPath;
  List<String> get pathHistory => _pathHistory;
  bool get isLoading => _isLoading;
  bool get isInitialized => _isInitialized;
  bool get showOnlyVideos => _showOnlyVideos;
  bool get isGridView => _isGridView;
  SortBy get sortBy => _sortBy;
  SortOrder get sortOrder => _sortOrder;
  String? get error => _error;
  bool get canGoBack => _pathHistory.isNotEmpty;
  bool get isSelectionMode => _isSelectionMode;
  Set<String> get selectedItems => _selectedItems;
  int get selectedCount => _selectedItems.length;

  // Public getter for root path
  String get getRootPath => _browser.getRootPath();

  Future<void> initialize() async {
    final rootPath = _browser.getRootPath();
    _currentPath = _resolveInitialPath(rootPath);
    _pathHistory
      ..clear()
      ..addAll(
        _persistedPathHistory.where((path) => path.startsWith(rootPath)),
      );

    if (_indexService.getSnapshot(rootPath) == null &&
        await _indexService.hasPersistedIndex(rootPath)) {
      await _indexService.ensureIndexed(rootPath);
    }

    await loadDirectory(_currentPath, addToHistory: false);
    _isInitialized = true;
    notifyListeners();
    unawaited(_indexService.ensureIndexed(rootPath));
  }

  Future<void> loadDirectory(String path,
      {bool addToHistory = true, bool silent = false}) async {
    if (!silent) {
      _isLoading = true;
      _error = null;
      notifyListeners();
    }

    if (addToHistory && _currentPath.isNotEmpty) {
      _pathHistory.add(_currentPath);
    }

    _currentPath = path;
    _persistNavigationState();
    // Don't force refresh browser cache on initial Load to be fast.
    // Refresh command will handle browser cache invalidation.
    final items = await _browser.listDirectory(path);

    // Filter items based on the best available data
    final filtered = _prepareVisibleItems(items);

    _items = filtered;

    _sortItems();
    if (!silent) _isLoading = false;
    notifyListeners();

    // Always schedule background hydration to ensure counts are fresh
    _scheduleFolderHydration(path, items);
  }

  List<FileItem> _prepareVisibleItems(List<FileItem> items) {
    final rootPath = _browser.getRootPath();
    final preparedItems = List<FileItem>.from(items);
    final snapshot = _indexService.getSnapshot(rootPath);

    for (final item in preparedItems) {
      if (!item.isDirectory) continue;

      final indexedCount =
          _indexService.getFolderVideoCount(rootPath, item.path);
      final cachedCount = _browser.getVideoCount(item.path);
      item.videoCount = indexedCount ?? cachedCount;
    }

    if (!_showOnlyVideos) return preparedItems;

    return preparedItems.where((item) {
      if (item.isVideo) return true;
      if (item.isDirectory) {
        final count = _indexService.getFolderVideoCount(rootPath, item.path) ??
            _browser.getVideoCount(item.path);

        // If we have an index (snapshot), we are strict about 0.
        // If count is null, it means folder is not indexed.
        if (snapshot != null) {
          // Key exists in snapshot -> use it. Missing key -> it's empty (0).
          final int indexedCount =
              _indexService.getFolderVideoCount(rootPath, item.path) ?? 0;
          return indexedCount > 0;
        }

        // Before the index is ready, only keep folders we already know contain videos.
        return (count ?? 0) > 0;
      }
      return false;
    }).toList();
  }

  Future<List<FileItem>> _filterFoldersWithVideos(
    List<FileItem> items,
  ) async {
    final filtered = <FileItem>[];
    final rootPath = _browser.getRootPath();

    final videos =
        items.where((item) => !item.isDirectory && item.isVideo).toList();
    filtered.addAll(videos);

    final folders = items.where((item) => item.isDirectory).toList();

    // Process folders concurrently
    final folderFutures = folders.map((item) async {
      final indexedCount =
          _indexService.getFolderVideoCount(rootPath, item.path);
      if (indexedCount != null) {
        _browser.setVideoCount(item.path, indexedCount);
        if (indexedCount > 0) {
          item.videoCount = indexedCount;
          return item;
        }
        return null;
      }

      final cachedCount = _browser.getVideoCount(item.path);
      if (cachedCount != null) {
        if (cachedCount > 0) {
          item.videoCount = cachedCount;
          return item;
        }
        return null;
      }

      final videoCount = await _countVideosRecursively(item.path);
      _browser.setVideoCount(item.path, videoCount);
      if (videoCount > 0) {
        item.videoCount = videoCount;
        return item;
      }
      return null;
    });

    final processedFolders = await Future.wait(folderFutures);
    for (final folder in processedFolders) {
      if (folder != null) filtered.add(folder);
    }

    return filtered;
  }

  void _scheduleFolderHydration(String path, List<FileItem> items) {
    if (!_showOnlyVideos) return;
    unawaited(_hydrateFolderVisibility(path, items));
  }

  Future<void> _hydrateFolderVisibility(
      String path, List<FileItem> items) async {
    final rootPath = _browser.getRootPath();
    final hasAllCounts = items.every((item) {
      if (!item.isDirectory) return true;
      final indexedCount =
          _indexService.getFolderVideoCount(rootPath, item.path);
      final cachedCount = _browser.getVideoCount(item.path);
      return indexedCount != null || cachedCount != null;
    });

    if (hasAllCounts) return;

    final filtered = await _filterFoldersWithVideos(items);

    if (_currentPath != path || !_showOnlyVideos) return;

    _items = filtered;
    _sortItems();
    notifyListeners();
  }

  Future<int> _countVideosRecursively(String path, {int depth = 0}) async {
    if (depth > 2) return 0; // Shallow for speed
    if (path.endsWith('/Android') || path.contains('/Android/')) return 0;

    int count = 0;
    try {
      final dir = Directory(path);
      if (!await dir.exists()) return 0;

      final entities = await dir.list(followLinks: false).toList();
      for (final entity in entities) {
        if (entity is File) {
          if (FileItem.isVideoFileName(entity.path.split('/').last)) count++;
        } else if (entity is Directory) {
          final name = entity.path.split('/').last;
          if (!name.startsWith('.')) {
            count +=
                await _countVideosRecursively(entity.path, depth: depth + 1);
          }
        }
      }
    } catch (_) {}
    return count;
  }

  Future<void> goBack() async {
    if (_pathHistory.isEmpty) return;
    final previousPath = _pathHistory.removeLast();
    _currentPath = previousPath;
    _persistNavigationState();
    final items = await _browser.listDirectory(previousPath);
    _items = _prepareVisibleItems(items);
    _sortItems();
    notifyListeners();
    _scheduleFolderHydration(previousPath, items);
  }

  Future<void> navigateToFolder(FileItem folder) async {
    if (!folder.isDirectory) return;
    await loadDirectory(folder.path);
  }

  String _resolveInitialPath(String rootPath) {
    final persistedPath = _persistedCurrentPath;
    if (persistedPath == null || !persistedPath.startsWith(rootPath)) {
      return rootPath;
    }
    return persistedPath;
  }

  void _persistNavigationState() {
    _persistedCurrentPath = _currentPath;
    _persistedPathHistory = List<String>.from(_pathHistory);
  }

  void toggleShowOnlyVideos() {
    _showOnlyVideos = !_showOnlyVideos;
    _browser.invalidatePath(_currentPath);
    _indexService.invalidate(_browser.getRootPath());
    loadDirectory(_currentPath, addToHistory: false);
  }

  void toggleViewMode() {
    _isGridView = !_isGridView;
    notifyListeners();
  }

  void setSortBy(SortBy sortBy) {
    if (_sortBy == sortBy) {
      _sortOrder = _sortOrder == SortOrder.ascending
          ? SortOrder.descending
          : SortOrder.ascending;
    } else {
      _sortBy = sortBy;
      _sortOrder = _defaultOrderFor(sortBy);
    }
    _sortItems();
    notifyListeners();
  }

  SortOrder _defaultOrderFor(SortBy sortBy) {
    switch (sortBy) {
      case SortBy.name:
        return SortOrder.ascending;
      case SortBy.date:
      case SortBy.size:
      case SortBy.videos:
        return SortOrder.descending;
    }
  }

  void _sortItems() {
    _items.sort((a, b) {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;

      int result;
      switch (_sortBy) {
        case SortBy.name:
          result = a.name.toLowerCase().compareTo(b.name.toLowerCase());
          break;
        case SortBy.date:
          result = a.modified.compareTo(b.modified);
          break;
        case SortBy.size:
          result = a.size.compareTo(b.size);
          break;
        case SortBy.videos:
          result = (a.videoCount ?? (a.isDirectory ? -1 : 0))
              .compareTo(b.videoCount ?? (b.isDirectory ? -1 : 0));
          break;
      }
      return _sortOrder == SortOrder.ascending ? result : -result;
    });
  }

  Future<void> refresh({bool silent = false}) async {
    final rootPath = _browser.getRootPath();
    final refreshedPath = _currentPath;

    _browser.invalidatePath(refreshedPath);
    await _indexService.refreshInBackground(rootPath);

    if (_currentPath != refreshedPath) return;

    await loadDirectory(refreshedPath, addToHistory: false, silent: silent);
  }

  void toggleSelection(String path) {
    if (_selectedItems.contains(path)) {
      _selectedItems.remove(path);
      if (_selectedItems.isEmpty) _isSelectionMode = false;
    } else {
      _selectedItems.add(path);
    }
    notifyListeners();
  }

  void enterSelectionMode(String initialPath) {
    _isSelectionMode = true;
    _selectedItems.add(initialPath);
    notifyListeners();
  }

  void exitSelectionMode() {
    _selectedItems.clear();
    _isSelectionMode = false;
    notifyListeners();
  }

  void selectAll() {
    for (final item in _items) {
      _selectedItems.add(item.path);
    }
    notifyListeners();
  }

  Future<void> deleteSelected() async {
    for (final path in _selectedItems) {
      try {
        final entity = File(path);
        if (await entity.exists()) {
          await entity.delete();
        } else {
          final dir = Directory(path);
          if (await dir.exists()) {
            await dir.delete(recursive: true);
          }
        }
      } catch (_) {}
    }
    exitSelectionMode();
    await refresh();
  }

  bool isSelected(String path) => _selectedItems.contains(path);
}
