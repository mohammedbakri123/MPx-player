import '../../../library/data/datasources/local_video_scanner.dart';
import '../../../library/domain/entities/video_file.dart';

class FavoritesRepository {
  static Future<List<VideoFile>> loadVideos() async {
    final folders = await VideoScanner().scanForVideos();
    final allVideos = <VideoFile>[];
    for (final folder in folders) {
      allVideos.addAll(folder.videos);
    }
    allVideos.sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
    return allVideos;
  }

  static List<VideoFile> loadDemoVideos() {
    return VideoScanner.getDemoData().first.videos;
  }
}
