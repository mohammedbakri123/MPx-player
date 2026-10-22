import '../../domain/entities/video_file.dart';
import '../../domain/repositories/library_repository.dart';
import '../datasources/file_system_library_datasource.dart';

class FileSystemLibraryRepository implements LibraryRepository {
  FileSystemLibraryRepository(this._datasource);

  final FileSystemLibraryDatasource _datasource;

  @override
  Future<bool> hasAccess() async => true;

  @override
  Future<bool> requestAccess() async => true;

  @override
  Future<List<VideoFile>> fetchVideos() => _datasource.fetchVideos();
}
