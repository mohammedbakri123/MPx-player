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
    final videoExtensions = [
      '.mp4',
      '.mkv',
      '.avi',
      '.mov',
      '.wmv',
      '.flv',
      '.webm',
      '.m4v',
      '.3gp',
      '.mpeg',
      '.mpg',
      '.ts',
      '.m2ts',
      '.mts',
      '.ogv',
      '.dv',
      '.rm',
      '.rmvb',
      '.asf',
      '.amv',
      '.mp2',
      '.m4v'
    ];

    for (final ext in videoExtensions) {
      if (lower.endsWith(ext)) return true;
    }
    return false;
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
