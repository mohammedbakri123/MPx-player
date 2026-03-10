import '../../../domain/entities/file_item.dart';
import '../../../domain/entities/video_file.dart';

class LibraryItemUi {
  static String relativeDate(DateTime date) {
    final diff = DateTime.now().difference(date);
    if (diff.inDays == 0) return 'today';
    if (diff.inDays == 1) return 'yesterday';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()}w ago';
    if (diff.inDays < 365) return '${(diff.inDays / 30).floor()}mo ago';
    return '${(diff.inDays / 365).floor()}y ago';
  }

  static String parentFolderName(String path) {
    final lastSeparator = path.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return '/';
    }
    return path.substring(0, lastSeparator).split('/').last;
  }

  static String parentFolderPath(String path) {
    final lastSeparator = path.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return '/';
    }
    return path.substring(0, lastSeparator);
  }

  static String folderVideoLabel(int? count) {
    if (count == null) {
      return 'Folder';
    }
    return count == 1 ? '1 video' : '$count videos';
  }

  static VideoFile videoFromFileItem(FileItem item) {
    return VideoFile.fromFileItem(item, parentFolderPath(item.path));
  }

  static VideoFile videoFromPath(String path, {DateTime? modified}) {
    final item = FileItem(
      path: path,
      name: path.split('/').last,
      isDirectory: false,
      size: 0,
      modified: modified ?? DateTime.now(),
    );
    return videoFromFileItem(item);
  }
}
