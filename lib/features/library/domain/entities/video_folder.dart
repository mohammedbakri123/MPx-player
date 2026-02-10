import 'video_file.dart';

class VideoFolder {
  final String path;
  final String name;
  final List<VideoFile> videos;

  VideoFolder({
    required this.path,
    required this.name,
    required this.videos,
  });

  int get videoCount => videos.length;

  int get totalSize {
    return videos.fold(0, (sum, video) => sum + video.size);
  }

  String get formattedSize {
    final size = totalSize;
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get formattedDate {
    if (videos.isEmpty) return 'Unknown';
    final latest =
        videos.map((v) => v.dateAdded).reduce((a, b) => a.isAfter(b) ? a : b);
    final now = DateTime.now();
    final diff = now.difference(latest);

    if (diff.inDays == 0) return 'Today';
    if (diff.inDays == 1) return 'Yesterday';
    if (diff.inDays < 7) return '${diff.inDays} days ago';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()} weeks ago';
    return '${(diff.inDays / 30).floor()} months ago';
  }
}
