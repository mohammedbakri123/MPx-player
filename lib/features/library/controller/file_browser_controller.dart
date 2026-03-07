import 'dart:io';
import 'package:flutter/foundation.dart';
import '../domain/entities/file_item.dart';
import '../data/datasources/directory_browser.dart';

enum SortBy { name, date, size }

enum SortOrder { ascending, descending }

class FileBrowserController extends ChangeNotifier {
  final DirectoryBrowser _browser = DirectoryBrowser();

  List<FileItem> _items = [];
  final List<String> _pathHistory = [];
  String _currentPath = '';
  bool _isLoading = false;
  bool _showOnlyVideos = true; // Default to TRUE as requested
  bool _hideEmptyFolders = true; // Default to TRUE as requested
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
  bool get showOnlyVideos => _showOnlyVideos;
  bool get isGridView => _isGridView;
  SortBy get sortBy => _sortBy;
  SortOrder get sortOrder => _sortOrder;
  String? get error => _error;
  bool get canGoBack => _pathHistory.isNotEmpty;
  bool get isSelectionMode => _isSelectionMode;
  Set<String> get selectedItems => _selectedItems;
  int get selectedCount => _selectedItems.length;

  String get currentFolderName {
    if (_currentPath.isEmpty) return 'Files';
    return _currentPath.split('/').last;
  }

  Future<void> initialize() async {
    _currentPath = _browser.getRootPath();
    await loadDirectory(_currentPath);
  }

  Future<void> loadDirectory(String path, {bool addToHistory = true}) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    if (addToHistory && _currentPath.isNotEmpty) {
      _pathHistory.add(_currentPath);
    }

    _currentPath = path;
    var items = await _browser.listDirectory(path);

    if (_hideEmptyFolders && _showOnlyVideos) {
      items = await _filterFoldersWithVideos(items);
    }

    _items = items;
    _sortItems();

    _isLoading = false;
    notifyListeners();
  }

  Future<List<FileItem>> _filterFoldersWithVideos(List<FileItem> items) async {
    final filtered = <FileItem>[];

    // Separate folders and videos
    final videos =
        items.where((item) => !item.isDirectory && item.isVideo).toList();
    filtered.addAll(videos);

    final folders = items.where((item) => item.isDirectory).toList();

    // Process folders concurrently to vastly improve load times
    final folderFutures = folders.map((item) async {
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
      if (folder != null) {
        filtered.add(folder);
      }
    }

    return filtered;
  }

  Future<int> _countVideosRecursively(String path, {int depth = 0}) async {
    if (depth > 3) return 0; // Reduced depth for performance

    // Skip massive system folders
    if (path.endsWith('/Android') || path.contains('/Android/')) return 0;

    int count = 0;
    try {
      final dir = Directory(path);
      if (!await dir.exists()) return 0;

      final entities = await dir.list(followLinks: false).toList();
      final futures = <Future<int>>[];

      for (final entity in entities) {
        if (entity is File) {
          final name = entity.path.split('/').last;
          if (_isVideoFileFast(name)) {
            count++;
          }
        } else if (entity is Directory) {
          final name = entity.path.split('/').last;
          if (!name.startsWith('.')) {
            futures.add(_countVideosRecursively(entity.path, depth: depth + 1));
          }
        }
      }

      if (futures.isNotEmpty) {
        final results = await Future.wait(futures);
        count += results.fold<int>(0, (sum, val) => sum + val);
      }
    } catch (_) {}

    return count;
  }

  bool _isVideoFileFast(String name) {
    final lower = name.toLowerCase();
    return lower.endsWith('.mp4') ||
        lower.endsWith('.mkv') ||
        lower.endsWith('.avi') ||
        lower.endsWith('.mov') ||
        lower.endsWith('.webm') ||
        lower.endsWith('.ts');
  }

  Future<void> goBack() async {
    if (_pathHistory.isEmpty) return;

    final previousPath = _pathHistory.removeLast();
    _currentPath = previousPath;
    var items = await _browser.listDirectory(previousPath);

    if (_hideEmptyFolders && _showOnlyVideos) {
      items = await _filterFoldersWithVideos(items);
    }

    _items = items;
    _sortItems();
    notifyListeners();
  }

  Future<void> navigateToFolder(FileItem folder) async {
    if (!folder.isDirectory) return;
    await loadDirectory(folder.path);
  }

  void toggleShowOnlyVideos() {
    _showOnlyVideos = !_showOnlyVideos;
    _hideEmptyFolders = _showOnlyVideos;
    _browser.invalidatePath(_currentPath);
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
      _sortOrder = SortOrder.ascending;
    }
    _sortItems();
    notifyListeners();
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
      }

      return _sortOrder == SortOrder.ascending ? result : -result;
    });
  }

  List<FileItem> get filteredItems {
    List<FileItem> result = _items;
    if (_showOnlyVideos) {
      result =
          result.where((item) => item.isVideo || item.isDirectory).toList();
    }
    return result;
  }

  void refresh() {
    _browser.invalidatePath(_currentPath);
    loadDirectory(_currentPath, addToHistory: false);
  }

  void toggleSelection(String path) {
    if (_selectedItems.contains(path)) {
      _selectedItems.remove(path);
      if (_selectedItems.isEmpty) {
        _isSelectionMode = false;
      }
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
    for (final item in filteredItems) {
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
      } catch (e) {
        // Skip errors
      }
    }
    exitSelectionMode();
    refresh();
  }

  bool isSelected(String path) => _selectedItems.contains(path);
}
