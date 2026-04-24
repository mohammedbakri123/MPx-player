import 'dart:isolate';
import '../domain/entities/file_item.dart';
import '../controller/file_browser_controller.dart';

Future<List<FileItem>> sortFileItemsIsolate(
  List<FileItem> items,
  SortBy sortBy,
  SortOrder sortOrder,
) async {
  if (items.length < 100) {
    return _sortInPlace(items, sortBy, sortOrder);
  }
  return Isolate.run(() => _sortInPlace(items, sortBy, sortOrder));
}

List<FileItem> _sortInPlace(
  List<FileItem> items,
  SortBy sortBy,
  SortOrder sortOrder,
) {
  final sorted = List<FileItem>.from(items);
  sorted.sort((a, b) {
    if (a.isDirectory && !b.isDirectory) return -1;
    if (!a.isDirectory && b.isDirectory) return 1;

    int result;
    switch (sortBy) {
      case SortBy.name:
        result = a.name.toLowerCase().compareTo(b.name.toLowerCase());
        break;
      case SortBy.date:
        result = a.modified.compareTo(b.modified);
        break;
      case SortBy.size:
        result = a.size.compareTo(b.size);
        break;
      case SortBy.videos:
        result = (a.videoCount ?? (a.isDirectory ? -1 : 0))
            .compareTo(b.videoCount ?? (b.isDirectory ? -1 : 0));
        break;
    }
    return sortOrder == SortOrder.ascending ? result : -result;
  });
  return sorted;
}
