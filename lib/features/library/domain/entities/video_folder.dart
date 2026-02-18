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

  // JSON serialization methods
  Map<String, dynamic> toJson() {
    return {
      'path': path,
      'name': name,
      'videos': videos.map((video) => video.toJson()).toList(),
    };
  }

  factory VideoFolder.fromJson(Map<String, dynamic> json) {
    return VideoFolder(
      path: json['path'] as String,
      name: json['name'] as String,
      videos: (json['videos'] as List)
          .map((videoJson) =>
              VideoFile.fromJson(videoJson as Map<String, dynamic>))
          .toList(),
    );
  }

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

  int get totalDuration {
    return videos.fold(0, (sum, video) => sum + video.duration);
  }

  String get formattedDuration {
    if (videos.isEmpty) return '0:00';
    final durationMs = totalDuration;
    final hours = durationMs ~/ 3600000;
    final minutes = (durationMs % 3600000) ~/ 60000;
    final seconds = (durationMs % 60000) ~/ 1000;

    if (hours > 0) {
      return '${hours}h ${minutes.toString().padLeft(2, '0')}m';
    } else if (minutes > 0) {
      return '${minutes}m ${seconds.toString().padLeft(2, '0')}s';
    } else {
      return '${seconds}s';
    }
  }
}
