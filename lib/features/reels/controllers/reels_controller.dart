import 'package:flutter/foundation.dart';
import 'package:mpx/features/reels/services/reel_service.dart';
import '../../library/domain/entities/video_file.dart';

enum ReelsSortOrder { dateDesc, dateAsc, nameAsc, shuffle }

class ReelsController extends ChangeNotifier {
  List<VideoFile> _reelsVideos = [];
  bool _isLoading = false;
  String? _error;
  String? _reelsFolderPath;
  final String? targetFolderPath;
  ReelsSortOrder _sortOrder = ReelsSortOrder.dateDesc;
  double _playbackSpeed = 1.0;
  bool _isPaused = false;

  List<VideoFile> get reelsVideos => _reelsVideos;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get reelsFolderPath => _reelsFolderPath;
  ReelsSortOrder get sortOrder => _sortOrder;
  double get playbackSpeed => _playbackSpeed;
  bool get isPaused => _isPaused;

  ReelsController({this.targetFolderPath}) {
    loadReels();
    if (targetFolderPath == null) {
      _loadFolderPath();
    } else {
      _reelsFolderPath = targetFolderPath;
    }
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
      if (targetFolderPath != null) {
        _reelsVideos =
            await ReelService.getVideosFromAnyFolder(targetFolderPath!);
      } else {
        _reelsVideos = await ReelService.getReelsVideos();
      }
      _applySort();
    } catch (e) {
      _error = 'Failed to load reels: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void changeSortOrder(ReelsSortOrder order) {
    _sortOrder = order;
    _applySort();
    notifyListeners();
  }

  void _applySort() {
    if (_reelsVideos.isEmpty) return;

    switch (_sortOrder) {
      case ReelsSortOrder.dateDesc:
        _reelsVideos.sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
        break;
      case ReelsSortOrder.dateAsc:
        _reelsVideos.sort((a, b) => a.dateAdded.compareTo(b.dateAdded));
        break;
      case ReelsSortOrder.nameAsc:
        _reelsVideos.sort(
            (a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()));
        break;
      case ReelsSortOrder.shuffle:
        _reelsVideos.shuffle();
        break;
    }
  }

  void togglePause() {
    _isPaused = !_isPaused;
    notifyListeners();
  }

  void setPaused(bool paused) {
    if (_isPaused != paused) {
      _isPaused = paused;
      notifyListeners();
    }
  }

  void cyclePlaybackSpeed() {
    const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    final currentIndex = speeds.indexOf(_playbackSpeed);
    _playbackSpeed = speeds[(currentIndex + 1) % speeds.length];
    notifyListeners();
  }

  void setPlaybackSpeed(double speed) {
    _playbackSpeed = speed;
    notifyListeners();
  }

  void resetPlaybackSpeed() {
    _playbackSpeed = 1.0;
    notifyListeners();
  }

  Future<void> importFolderToReels(String folderPath) async {
    if (targetFolderPath != null) {
      return;
    }
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await ReelService.importFolder(folderPath);
      await loadReels();
    } catch (e) {
      _error = 'Failed to import folder: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> importVideoToReels(String filePath) async {
    if (targetFolderPath != null) {
      return;
    }
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await ReelService.importVideoFile(filePath);
      await loadReels();
    } catch (e) {
      _error = 'Failed to import video: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
