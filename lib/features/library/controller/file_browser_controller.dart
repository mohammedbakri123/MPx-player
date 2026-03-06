import 'package:flutter/foundation.dart';
import '../domain/entities/file_item.dart';
import '../data/datasources/directory_browser.dart';

enum SortBy { name, date, size }

enum SortOrder { ascending, descending }

class FileBrowserController extends ChangeNotifier {
  final DirectoryBrowser _browser = DirectoryBrowser();

  List<FileItem> _items = [];
  List<String> _pathHistory = [];
  String _currentPath = '';
  bool _isLoading = false;
  bool _showOnlyVideos = false;
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
    _items = await _browser.listDirectory(path);
    _sortItems();

    _isLoading = false;
    notifyListeners();
  }

  Future<void> goBack() async {
    if (_pathHistory.isEmpty) return;

    final previousPath = _pathHistory.removeLast();
    _currentPath = previousPath;
    _items = await _browser.listDirectory(previousPath);
    _sortItems();
    notifyListeners();
  }

  Future<void> navigateToFolder(FileItem folder) async {
    if (!folder.isDirectory) return;
    await loadDirectory(folder.path);
  }

  void toggleShowOnlyVideos() {
    _showOnlyVideos = !_showOnlyVideos;
    notifyListeners();
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
      result = result.where((item) => item.isVideo).toList();
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

  bool isSelected(String path) => _selectedItems.contains(path);
}
