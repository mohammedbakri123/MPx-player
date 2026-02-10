class VideoFile {
  final String id;
  final String path;
  final String title;
  final String folderPath;
  final String folderName;
  final int size;
  final int duration;
  final DateTime dateAdded;
  final int? width;
  final int? height;
  String? thumbnailPath;

  VideoFile({
    required this.id,
    required this.path,
    required this.title,
    required this.folderPath,
    required this.folderName,
    required this.size,
    required this.duration,
    required this.dateAdded,
    this.width,
    this.height,
    this.thumbnailPath,
  });

  String get formattedSize {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get formattedDuration {
    final minutes = (duration ~/ 60000);
    final seconds = ((duration % 60000) ~/ 1000);
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  String get resolution {
    if (width != null && height != null) {
      final h = height!;
      if (h >= 2160) return '4K';
      if (h >= 1080) return '1080P';
      if (h >= 720) return '720P';
      if (h >= 480) return '480P';
      return '${h}P';
    }
    return 'Unknown';
  }

  String get formattedDate {
    final now = DateTime.now();
    final diff = now.difference(dateAdded);

    if (diff.inDays == 0) {
      if (diff.inHours == 0) {
        if (diff.inMinutes == 0) return 'Just now';
        return '${diff.inMinutes} min ago';
      }
      return '${diff.inHours} hours ago';
    }
    if (diff.inDays == 1) return 'Yesterday';
    if (diff.inDays < 7) return '${diff.inDays} days ago';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()} weeks ago';
    if (diff.inDays < 365) return '${(diff.inDays / 30).floor()} months ago';
    return '${(diff.inDays / 365).floor()} years ago';
  }
}
