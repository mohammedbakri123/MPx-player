class VideoFile {
  const VideoFile({
    required this.id,
    required this.path,
    required this.name,
    required this.durationMs,
    required this.sizeBytes,
    required this.dateModifiedMs,
    required this.folderPath,
  });

  final String id;
  final String path;
  final String name;
  final int durationMs;
  final int sizeBytes;
  final int dateModifiedMs;
  final String folderPath;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is VideoFile && other.id == id && other.path == path;

  @override
  int get hashCode => Object.hash(id, path);
}
