import 'package:flutter/foundation.dart';

import '../data/library_database.dart';
import '../models/file_item.dart';
import '../models/folder_item.dart';
import '../models/library_sort_option.dart';

class LibraryController extends ChangeNotifier {
  LibraryController({LibraryDatabase? database})
      : _database = database ?? LibraryDatabase();

  final LibraryDatabase _database;

  bool _isLoading = false;
  String? _errorMessage;
  FolderItem? _currentFolder;
  List<FolderItem> _breadcrumbs = <FolderItem>[];
  List<FolderItem> _folders = <FolderItem>[];
  List<FileItem> _files = <FileItem>[];

  String _searchQuery = '';
  List<FileItem> _searchResults = <FileItem>[];
  FileKind? _filterKind;

  LibrarySortOption _sortOption = LibrarySortOption.name;
  bool _sortAscending = true;
  bool _isGridView = false;

  // Active playback state for Now Playing Dock
  FileItem? _activeFile;
  bool _isPlaying = false;
  double _playbackProgress = 0.0;

  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  FolderItem? get currentFolder => _currentFolder;
  List<FolderItem> get breadcrumbs => List<FolderItem>.unmodifiable(_breadcrumbs);
  List<FolderItem> get folders => List<FolderItem>.unmodifiable(_folders);
  String get searchQuery => _searchQuery;
  List<FileItem> get searchResults => List<FileItem>.unmodifiable(_searchResults);
  FileKind? get filterKind => _filterKind;

  LibrarySortOption get sortOption => _sortOption;
  bool get sortAscending => _sortAscending;
  bool get isGridView => _isGridView;

  FileItem? get activeFile => _activeFile;
  bool get isPlaying => _isPlaying;
  double get playbackProgress => _playbackProgress;

  List<FileItem> get files {
    if (_filterKind == null) {
      return List<FileItem>.unmodifiable(_files);
    }
    return List<FileItem>.unmodifiable(
      _files.where((FileItem f) => f.kind == _filterKind),
    );
  }

  bool get isRoot => _currentFolder == null;

  Future<void> load({int? folderId}) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      if (folderId != null) {
        _currentFolder = await _database.getFolderById(folderId);
        _breadcrumbs = await _database.getBreadcrumbs(folderId);
        _folders = await _database.getFolders(
          parentId: folderId,
          sortBy: _sortOption,
          ascending: _sortAscending,
        );
        _files = await _database.getFiles(
          folderId,
          sortBy: _sortOption,
          ascending: _sortAscending,
        );
      } else {
        _currentFolder = null;
        _breadcrumbs = <FolderItem>[];
        _folders = await _database.getFolders(
          parentId: null,
          sortBy: _sortOption,
          ascending: _sortAscending,
        );
        _files = <FileItem>[];
      }
    } catch (e) {
      _errorMessage = 'Failed to load library: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> openFolder(FolderItem folder) async {
    _searchQuery = '';
    _searchResults = <FileItem>[];
    await load(folderId: folder.id);
  }

  Future<void> navigateUp() async {
    if (_breadcrumbs.isEmpty || _currentFolder == null) {
      return;
    }
    if (_breadcrumbs.length <= 1) {
      await load(folderId: null);
    } else {
      final FolderItem parent = _breadcrumbs[_breadcrumbs.length - 2];
      await load(folderId: parent.id);
    }
  }

  Future<void> navigateToBreadcrumb(FolderItem? folder) async {
    _searchQuery = '';
    _searchResults = <FileItem>[];
    if (folder == null) {
      await load(folderId: null);
    } else {
      await load(folderId: folder.id);
    }
  }

  Future<void> setSearchQuery(String query) async {
    _searchQuery = query;
    if (query.trim().isEmpty) {
      _searchResults = <FileItem>[];
      notifyListeners();
      return;
    }

    _isLoading = true;
    notifyListeners();

    try {
      final List<FileItem> results = await _database.searchFiles(query);
      if (_filterKind != null) {
        _searchResults = results.where((FileItem f) => f.kind == _filterKind).toList();
      } else {
        _searchResults = results;
      }
    } catch (e) {
      _errorMessage = 'Search error: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void setFilterKind(FileKind? kind) {
    if (_filterKind == kind) {
      _filterKind = null;
    } else {
      _filterKind = kind;
    }
    if (_searchQuery.isNotEmpty) {
      setSearchQuery(_searchQuery);
    } else {
      notifyListeners();
    }
  }

  void toggleViewMode() {
    _isGridView = !_isGridView;
    notifyListeners();
  }

  Future<void> setSortOption(LibrarySortOption option) async {
    if (_sortOption == option) {
      _sortAscending = !_sortAscending;
    } else {
      _sortOption = option;
      _sortAscending = true;
    }
    await refresh();
  }

  void playFile(FileItem file) {
    _activeFile = file;
    _isPlaying = true;
    _playbackProgress = 0.28;
    notifyListeners();
  }

  void togglePlayPause() {
    _isPlaying = !_isPlaying;
    notifyListeners();
  }

  void stopPlayback() {
    _activeFile = null;
    _isPlaying = false;
    _playbackProgress = 0.0;
    notifyListeners();
  }

  void seek(double progress) {
    _playbackProgress = progress.clamp(0.0, 1.0);
    notifyListeners();
  }

  Future<void> addFolder(String name) async {
    if (name.trim().isEmpty) return;
    try {
      await _database.insertFolder(name, parentId: _currentFolder?.id);
      await refresh();
    } catch (e) {
      _errorMessage = 'Could not create folder: $e';
      notifyListeners();
    }
  }

  Future<void> deleteFolder(int id) async {
    try {
      await _database.deleteFolder(id);
      await refresh();
    } catch (e) {
      _errorMessage = 'Could not delete folder: $e';
      notifyListeners();
    }
  }

  Future<void> deleteFile(int id) async {
    try {
      if (_activeFile?.id == id) {
        stopPlayback();
      }
      await _database.deleteFile(id);
      await refresh();
    } catch (e) {
      _errorMessage = 'Could not delete file: $e';
      notifyListeners();
    }
  }

  Future<void> resetDummyData() async {
    _isLoading = true;
    notifyListeners();
    try {
      await _database.seedDummyData(force: true);
      await load(folderId: null);
    } catch (e) {
      _errorMessage = 'Could not reset dummy data: $e';
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refresh() async {
    await load(folderId: _currentFolder?.id);
  }
}
