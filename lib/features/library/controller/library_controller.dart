import 'package:flutter/foundation.dart';

import '../../../../core/utils/logger.dart';
import '../domain/entities/video_file.dart';
import '../domain/repositories/library_repository.dart';

enum LibraryStatus { loading, needsPermission, empty, ready, failure }

@immutable
class LibraryState {
  const LibraryState({
    this.status = LibraryStatus.loading,
    this.videos = const <VideoFile>[],
    this.error,
  });

  final LibraryStatus status;
  final List<VideoFile> videos;
  final String? error;

  LibraryState copyWith({
    LibraryStatus? status,
    List<VideoFile>? videos,
    String? error,
  }) {
    return LibraryState(
      status: status ?? this.status,
      videos: videos ?? this.videos,
      error: error ?? this.error,
    );
  }
}

class LibraryController extends ChangeNotifier {
  static const String _tag = 'LibraryController';

  LibraryController(this._repository);

  final LibraryRepository _repository;

  LibraryState _state = const LibraryState();
  bool _disposed = false;

  LibraryState get state => _state;

  LibraryStatus get status => _state.status;

  List<VideoFile> get videos => _state.videos;

  Future<void> load() async {
    _update(const LibraryState());
    try {
      final bool hasAccess = await _repository.hasAccess();
      if (_disposed) return;
      if (!hasAccess) {
        final bool granted = await _repository.requestAccess();
        if (_disposed) return;
        if (!granted) {
          _update(const LibraryState(status: LibraryStatus.needsPermission));
          return;
        }
      }
      await _fetchVideos();
    } catch (e, s) {
      AppLogger.e(_tag, 'Failed to load library: $e', s);
      if (_disposed) return;
      _update(
        LibraryState(
          status: LibraryStatus.failure,
          error: e.toString(),
        ),
      );
    }
  }

  Future<void> _fetchVideos() async {
    final List<VideoFile> videos = await _repository.fetchVideos();
    if (_disposed) return;
    if (videos.isEmpty) {
      _update(const LibraryState(status: LibraryStatus.empty));
      return;
    }
    _update(
      LibraryState(status: LibraryStatus.ready, videos: videos),
    );
  }

  void _update(LibraryState state) {
    _state = state;
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
