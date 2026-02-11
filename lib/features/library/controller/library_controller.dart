import 'package:flutter/foundation.dart';
import '../../../../core/services/logger_service.dart';
import '../domain/entities/video_folder.dart';
import '../data/datasources/local_video_scanner.dart';

/// Controller for managing library state and business logic.
///
/// This controller follows the clean architecture pattern:
/// - Manages loading state
/// - Coordinates data fetching from VideoScanner
/// - Handles folder grouping and filtering
/// - Provides ChangeNotifier for UI updates
///
/// **Responsibilities:**
/// - Loading and refreshing video library
/// - View mode state (list/grid)
/// - Demo mode handling
/// - State management (ChangeNotifier)
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

  /// Toggles between list and grid view modes.
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

  /// Internal method to load videos from scanner.
  Future<void> _loadVideos({required bool forceRefresh}) async {
    _isLoading = true;
    _errorMessage = null;
    // Don't notify listeners immediately to preserve current UI during refresh
    notifyListeners();

    try {
      final folders = await _scanner.scanForVideos(
        forceRefresh: forceRefresh,
        onProgress: (progress, status) {
          // Update progress if needed - for now we just log it
          AppLogger.i('Scan progress: ${(progress * 100).round()}% - $status');
        },
      );
      _folders = folders;
      _isLoading = false;
      _errorMessage = null;
    } catch (e) {
      _folders = [];
      _isLoading = false;
      _errorMessage = 'Failed to load videos: ${e.toString()}';
      debugPrint('LibraryController: Error loading videos: $e');
    }

    notifyListeners();
  }

  @override
  void dispose() {
    // Clean up if needed
    super.dispose();
  }
}
