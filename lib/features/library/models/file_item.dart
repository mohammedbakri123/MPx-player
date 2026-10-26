enum FileKind {
  audio,
  video,
  other;

  static FileKind fromString(String value) {
    switch (value.toLowerCase()) {
      case 'audio':
        return FileKind.audio;
      case 'video':
        return FileKind.video;
      default:
        return FileKind.other;
    }
  }
}

class FileItem {
  const FileItem({
    required this.id,
    required this.folderId,
    required this.name,
    required this.path,
    required this.extension,
    required this.kind,
    required this.sizeBytes,
    required this.durationMs,
    required this.createdAt,
    required this.modifiedAt,
  });

  final int id;
  final int folderId;
  final String name;
  final String path;
  final String extension;
  final FileKind kind;
  final int sizeBytes;
  final int durationMs;
  final DateTime createdAt;
  final DateTime modifiedAt;

  bool get isAudio => kind == FileKind.audio;
  bool get isVideo => kind == FileKind.video;

  String get formattedSize {
    if (sizeBytes < 1024) {
      return '$sizeBytes B';
    } else if (sizeBytes < 1024 * 1024) {
      return '${(sizeBytes / 1024).toStringAsFixed(1)} KB';
    } else if (sizeBytes < 1024 * 1024 * 1024) {
      return '${(sizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    } else {
      return '${(sizeBytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
    }
  }

  String get formattedDuration {
    if (durationMs <= 0) {
      return '--:--';
    }
    final Duration d = Duration(milliseconds: durationMs);
    final int hours = d.inHours;
    final int minutes = d.inMinutes.remainder(60);
    final int seconds = d.inSeconds.remainder(60);

    final String minStr = minutes.toString().padLeft(2, '0');
    final String secStr = seconds.toString().padLeft(2, '0');

    if (hours > 0) {
      return '$hours:$minStr:$secStr';
    }
    return '$minStr:$secStr';
  }

  factory FileItem.fromMap(Map<String, dynamic> map) {
    return FileItem(
      id: map['id'] as int,
      folderId: map['folder_id'] as int,
      name: map['name'] as String,
      path: map['path'] as String,
      extension: map['extension'] as String? ?? '',
      kind: FileKind.fromString(map['kind'] as String? ?? 'other'),
      sizeBytes: (map['size_bytes'] as num?)?.toInt() ?? 0,
      durationMs: (map['duration_ms'] as num?)?.toInt() ?? 0,
      createdAt: DateTime.fromMillisecondsSinceEpoch(
        (map['created_at'] as num?)?.toInt() ?? 0,
      ),
      modifiedAt: DateTime.fromMillisecondsSinceEpoch(
        (map['modified_at'] as num?)?.toInt() ?? 0,
      ),
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      if (id > 0) 'id': id,
      'folder_id': folderId,
      'name': name,
      'path': path,
      'extension': extension,
      'kind': kind.name,
      'size_bytes': sizeBytes,
      'duration_ms': durationMs,
      'created_at': createdAt.millisecondsSinceEpoch,
      'modified_at': modifiedAt.millisecondsSinceEpoch,
    };
  }
}
