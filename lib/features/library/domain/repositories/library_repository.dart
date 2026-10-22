import '../entities/video_file.dart';

abstract class LibraryRepository {
  Future<bool> hasAccess();

  Future<bool> requestAccess();

  Future<List<VideoFile>> fetchVideos();
}
