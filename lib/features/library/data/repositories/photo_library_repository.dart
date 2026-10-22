import '../../domain/entities/video_file.dart';
import '../../domain/repositories/library_repository.dart';
import '../datasources/media_library_datasource.dart';

class PhotoLibraryRepository implements LibraryRepository {
  PhotoLibraryRepository(this._datasource);

  final MediaLibraryDatasource _datasource;

  @override
  Future<bool> hasAccess() => _datasource.hasAccess();

  @override
  Future<bool> requestAccess() => _datasource.requestAccess();

  @override
  Future<List<VideoFile>> fetchVideos() => _datasource.fetchVideos();
}
