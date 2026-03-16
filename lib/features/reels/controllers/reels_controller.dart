import 'package:flutter/foundation.dart';
import 'package:mpx/features/reels/services/reel_service.dart';
import '../../library/domain/entities/video_file.dart';

class ReelsController extends ChangeNotifier {
  List<VideoFile> _reelsVideos = [];
  bool _isLoading = false;
  String? _error;
  String? _reelsFolderPath;

  List<VideoFile> get reelsVideos => _reelsVideos;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get reelsFolderPath => _reelsFolderPath;

  ReelsController() {
    loadReels();
    _loadFolderPath();
  }

  Future<void> _loadFolderPath() async {
    _reelsFolderPath = await ReelService.getReelsFolderPath();
    notifyListeners();
  }

  Future<void> loadReels() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _reelsVideos = await ReelService.getReelsVideos();
    } catch (e) {
      _error = 'Failed to load reels: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> importFolderToReels(String folderPath) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await ReelService.importFolder(folderPath);
      await loadReels(); // Reload reels after import
    } catch (e) {
      _error = 'Failed to import folder: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> importVideoToReels(String filePath) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await ReelService.importVideoFile(filePath);
      await loadReels(); // Reload reels after import
    } catch (e) {
      _error = 'Failed to import video: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
