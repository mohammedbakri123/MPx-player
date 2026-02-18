import '../../../domain/entities/video_file.dart';
import '../../../domain/entities/video_folder.dart';

/// Helper class for generating demo data
class DemoDataHelper {
  /// Get demo video folders for testing
  static List<VideoFolder> getDemoData() {
    final demoFolder = VideoFolder(
      path: '/storage/emulated/0/Demo',
      name: 'Demo Videos',
      videos: [
        VideoFile(
          id: 'demo1',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
          title: 'Big Buck Bunny',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 158000000,
          duration: 596000,
          dateAdded: DateTime.now().subtract(const Duration(days: 1)),
        ),
        VideoFile(
          id: 'demo2',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
          title: 'Elephants Dream',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 105700000,
          duration: 653000,
          dateAdded: DateTime.now().subtract(const Duration(days: 2)),
        ),
        VideoFile(
          id: 'demo3',
          path:
              'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
          title: 'Tears of Steel',
          folderPath: '/storage/emulated/0/Demo',
          folderName: 'Demo Videos',
          size: 154000000,
          duration: 734000,
          dateAdded: DateTime.now().subtract(const Duration(days: 3)),
        ),
      ],
    );

    return [demoFolder];
  }
}
