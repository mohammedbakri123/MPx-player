import 'package:flutter/foundation.dart';
import '../../../../core/services/logger_service.dart';

import '../domain/entities/video_folder.dart';
import '../domain/entities/video_file.dart';
import '../data/datasources/local_video_scanner.dart';
import '../data/workers/video_metadata_worker.dart';

/// Controller for managing library state and business logic.
///
/// This controller follows the clean architecture pattern:
/// - Manages loading state
/// - Coordinates data fetching from VideoScanner
/// - Handles folder grouping and filtering
/// - Provides ChangeNotifier for UI updates
/// - Implements lazy loading for folder contents
///
/// **Responsibilities:**
/// - Loading and refreshing video library
/// - View mode state (list/grid)
/// - Demo mode handling
/// - State management (ChangeNotifier)
/// - Lazy loading of folder videos
///
/// **Does NOT:**
/// - Directly perform file system operations (delegates to scanner)
/// - Handle UI rendering (presentation layer responsibility)
class LibraryController extends ChangeNotifier {
  final VideoScanner _scanner;

  // State
  List<VideoFolder> _folders = [];
  bool _isLoading = true;
  bool _isGridView = false;
  String? _errorMessage;

  // Lazy loading state
  final Map<String, bool> _loadedFolders = {};
  final Map<String, List<VideoFile>> _folderVideoCache = {};

  // Getters
  List<VideoFolder> get folders => _folders;
  bool get isLoading => _isLoading;
  bool get isGridView => _isGridView;
  bool get hasError => _errorMessage != null;
  String? get errorMessage => _errorMessage;
  bool get isEmpty => _folders.isEmpty && !_isLoading;

  /// Creates a LibraryController with dependency injection.
  ///
  /// [scanner] - The video scanner for fetching video data.
  LibraryController(this._scanner);

  /// Loads videos from storage.
  ///
  /// This should be called when the screen initializes.
  Future<void> load() async {
    await _loadVideos(forceRefresh: false);
  }

  /// Refreshes the video library by rescanning storage.
  ///
  /// This is typically called when user performs pull-to-refresh.
  Future<void> refresh() async {
    await _loadVideos(forceRefresh: true);
  }

  /// Toggle between list and grid view modes.
  void toggleViewMode() {
    _isGridView = !_isGridView;
    notifyListeners();
  }

  /// Sets the view mode explicitly.
  void setViewMode(bool isGrid) {
    if (_isGridView != isGrid) {
      _isGridView = isGrid;
      notifyListeners();
    }
  }

  /// Loads demo data for testing/preview purposes.
  void loadDemoData() {
    _folders = VideoScanner.getDemoData();
    _isLoading = false;
    _errorMessage = null;
    notifyListeners();
  }

  /// Check if folder videos are loaded
  bool isFolderLoaded(String folderPath) {
    return _loadedFolders[folderPath] ?? false;
  }

  /// Lazily load videos for a specific folder
  Future<List<VideoFile>> loadFolderVideos(String folderPath) async {
    // Check cache first
    if (_folderVideoCache.containsKey(folderPath)) {
      AppLogger.d('Returning cached videos for folder: $folderPath');
      return _folderVideoCache[folderPath]!;
    }

    try {
      AppLogger.i('Lazy loading videos for folder: $folderPath');
      final videos = await _scanner.getVideosInFolder(folderPath);

      // Cache the videos
      _folderVideoCache[folderPath] = videos;
      _loadedFolders[folderPath] = true;

      AppLogger.i('Loaded ${videos.length} videos for folder: $folderPath');
      return videos;
    } catch (e) {
      AppLogger.e('Error loading folder videos: $e');
      return [];
    }
  }

  /// Invalidate cached videos for a folder
  void invalidateFolder(String folderPath) {
    _folderVideoCache.remove(folderPath);
    _loadedFolders.remove(folderPath);
    AppLogger.d('Invalidated cache for folder: $folderPath');
  }

  /// Clear all folder video caches
  void clearFolderCaches() {
    _folderVideoCache.clear();
    _loadedFolders.clear();
    AppLogger.i('Cleared all folder caches');
  }

  /// Get cache stats
  Map<String, dynamic> get cacheStats {
    return {
      'loadedFolders': _loadedFolders.length,
      'folderVideoCacheSize': _folderVideoCache.length,
      'totalFolders': _folders.length,
    };
  }

  /// Internal method to load videos from scanner.
  Future<void> _loadVideos({required bool forceRefresh}) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final folders = await _scanner.scanForVideos(
        forceRefresh: forceRefresh,
        enableWatching: true,
        onProgress: (progress, status) {
          AppLogger.i('Scan progress: ${(progress * 100).round()}% - $status');
        },
      );

      // Only update if we got new data
      if (folders.isNotEmpty) {
        _folders = folders;
        _errorMessage = null;

        // Clear lazy loading caches on full refresh
        if (forceRefresh) {
          clearFolderCaches();
          VideoMetadataWorker().clearCache();
        }

        // Start background processing for thumbnails and metadata
        await _startBackgroundProcessing();
      }

      _isLoading = false;
    } catch (e) {
      _folders = [];
      _isLoading = false;
      _errorMessage = 'Failed to load videos: ${e.toString()}';
      debugPrint('LibraryController: Error loading videos: $e');
    }

    notifyListeners();
  }

  /// Start background processing for thumbnails and metadata
  Future<void> _startBackgroundProcessing() async {
    // Collect all videos from all folders
    final allVideos = <VideoFile>[];
    for (final folder in _folders) {
      allVideos.addAll(folder.videos);
    }

    if (allVideos.isEmpty) return;

    AppLogger.i(
        'Starting background processing for ${allVideos.length} videos...');

    // Process remaining videos (skip first 10, already done during scan)
    final remainingVideos = allVideos.skip(10).toList();
    if (remainingVideos.isNotEmpty) {
      VideoMetadataWorker().processVideos(remainingVideos);
    }
  }

  @override
  void dispose() {
    // Clean up caches
    clearFolderCaches();
    super.dispose();
  }
}
