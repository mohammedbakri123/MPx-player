class FolderItem {
  const FolderItem({
    required this.id,
    required this.name,
    this.parentId,
    required this.createdAt,
    this.subfolderCount = 0,
    this.fileCount = 0,
  });

  final int id;
  final String name;
  final int? parentId;
  final DateTime createdAt;
  final int subfolderCount;
  final int fileCount;

  int get totalItems => subfolderCount + fileCount;

  factory FolderItem.fromMap(Map<String, dynamic> map) {
    return FolderItem(
      id: map['id'] as int,
      name: map['name'] as String,
      parentId: map['parent_id'] as int?,
      createdAt: DateTime.fromMillisecondsSinceEpoch(map['created_at'] as int),
      subfolderCount: (map['subfolder_count'] as num?)?.toInt() ?? 0,
      fileCount: (map['file_count'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      if (id > 0) 'id': id,
      'name': name,
      'parent_id': parentId,
      'created_at': createdAt.millisecondsSinceEpoch,
    };
  }

  FolderItem copyWith({
    int? id,
    String? name,
    int? parentId,
    DateTime? createdAt,
    int? subfolderCount,
    int? fileCount,
  }) {
    return FolderItem(
      id: id ?? this.id,
      name: name ?? this.name,
      parentId: parentId ?? this.parentId,
      createdAt: createdAt ?? this.createdAt,
      subfolderCount: subfolderCount ?? this.subfolderCount,
      fileCount: fileCount ?? this.fileCount,
    );
  }
}
