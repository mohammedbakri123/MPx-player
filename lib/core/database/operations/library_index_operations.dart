import 'package:sqflite/sqflite.dart';

mixin LibraryIndexDatabaseOperations {
  Future<void> saveLibraryIndexMetadata(
      Database db, String rootPath, DateTime indexedAt) async {
    await db.insert(
      'library_index_metadata',
      {
        'root_path': rootPath,
        'indexed_at': indexedAt.millisecondsSinceEpoch,
        'updated_at': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<Map<String, dynamic>?> getLibraryIndexMetadata(
      Database db, String rootPath) async {
    final List<Map<String, dynamic>> maps = await db.query(
      'library_index_metadata',
      where: 'root_path = ?',
      whereArgs: [rootPath],
    );
    if (maps.isNotEmpty) {
      return maps.first;
    }
    return null;
  }

  Future<void> deleteLibraryIndexMetadata(Database db, String rootPath) async {
    await db.delete(
      'library_index_metadata',
      where: 'root_path = ?',
      whereArgs: [rootPath],
    );
  }
}
