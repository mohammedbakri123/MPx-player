class FileItem {
  final String path;
  final String name;
  final bool isDirectory;
  final int size;
  final DateTime modified;
  final DateTime? dateAdded;
  int? videoCount;

  FileItem({
    required this.path,
    required this.name,
    required this.isDirectory,
    required this.size,
    required this.modified,
    this.dateAdded,
    this.videoCount,
  });

  bool get isVideo {
    if (isDirectory) return false;
    return _isVideoFile(name);
  }

  static bool _isVideoFile(String name) {
    final lower = name.toLowerCase();
    return lower.endsWith('.mp4') ||
        lower.endsWith('.mkv') ||
        lower.endsWith('.webm') ||
        lower.endsWith('.mov') ||
        lower.endsWith('.avi') ||
        lower.endsWith('.wmv') ||
        lower.endsWith('.flv') ||
        lower.endsWith('.3gp') ||
        lower.endsWith('.m4v') ||
        lower.endsWith('.mpeg') ||
        lower.endsWith('.mpg') ||
        lower.endsWith('.ts') ||
        lower.endsWith('.m2ts') ||
        lower.endsWith('.mts') ||
        lower.endsWith('.ogv') ||
        lower.endsWith('.dv') ||
        lower.endsWith('.rm') ||
        lower.endsWith('.rmvb') ||
        lower.endsWith('.asf') ||
        lower.endsWith('.amv') ||
        lower.endsWith('.mp2');
  }

  static bool isVideoFileName(String name) => _isVideoFile(name);

  String get extension {
    if (isDirectory) return '';
    final dotIndex = name.lastIndexOf('.');
    return dotIndex != -1 ? name.substring(dotIndex + 1).toLowerCase() : '';
  }

  String get formattedSize {
    if (isDirectory) return '';
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
