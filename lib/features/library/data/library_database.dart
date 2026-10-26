import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xdg_directories/xdg_directories.dart';

import '../models/file_item.dart';
import '../models/folder_item.dart';
import '../models/library_sort_option.dart';

class LibraryDatabase {
  LibraryDatabase({
    DatabaseFactory? factory,
    String? path,
    Database? database,
  })  : _factory = factory ?? databaseFactoryFfi,
        _path = path,
        _database = database;

  final DatabaseFactory _factory;
  final String? _path;
  Database? _database;
  Future<Database>? _opening;

  Future<Database> get database {
    final Database? existing = _database;
    if (existing != null) {
      return Future<Database>.value(existing);
    }
    return _opening ??= _open();
  }

  Future<Database> _open() async {
    final String path = _path ?? _defaultPath();
    if (path != inMemoryDatabasePath) {
      await Directory(p.dirname(path)).create(recursive: true);
    }

    final Database db = await _factory.openDatabase(
      path,
      options: OpenDatabaseOptions(
        version: 1,
        onConfigure: (Database d) async {
          await d.execute('PRAGMA foreign_keys = ON');
        },
        onCreate: (Database d, int version) async {
          await d.execute('''
            CREATE TABLE folders (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              parent_id INTEGER,
              created_at INTEGER NOT NULL,
              FOREIGN KEY (parent_id) REFERENCES folders(id) ON DELETE CASCADE
            )
          ''');
          await d.execute('''
            CREATE TABLE files (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              folder_id INTEGER NOT NULL,
              name TEXT NOT NULL,
              path TEXT NOT NULL,
              extension TEXT NOT NULL,
              kind TEXT NOT NULL,
              size_bytes INTEGER NOT NULL,
              duration_ms INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              modified_at INTEGER NOT NULL,
              FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE CASCADE
            )
          ''');
          await d.execute('CREATE INDEX idx_folders_parent_id ON folders(parent_id)');
          await d.execute('CREATE INDEX idx_files_folder_id ON files(folder_id)');
          await d.execute('CREATE INDEX idx_files_kind ON files(kind)');
        },
      ),
    );

    _database = db;
    _opening = null;

    // Check if initial dummy data needs to be populated
    final List<Map<String, dynamic>> countRes =
        await db.rawQuery('SELECT COUNT(*) as count FROM folders');
    final int count = (countRes.first['count'] as num?)?.toInt() ?? 0;
    if (count == 0) {
      await seedDummyData(force: true);
    }

    return db;
  }

  static String _defaultPath() {
    try {
      return p.join(dataHome.path, 'mpx', 'library_dummy.db');
    } on StateError {
      final String home =
          Platform.environment['HOME'] ?? Directory.systemTemp.path;
      return p.join(home, '.local', 'share', 'mpx', 'library_dummy.db');
    }
  }

  Future<List<FolderItem>> getFolders({
    int? parentId,
    LibrarySortOption sortBy = LibrarySortOption.name,
    bool ascending = true,
  }) async {
    final Database db = await database;
    final String dir = ascending ? 'ASC' : 'DESC';
    final String orderClause = switch (sortBy) {
      LibrarySortOption.name => 'f.name COLLATE NOCASE $dir',
      LibrarySortOption.date => 'f.created_at $dir',
      LibrarySortOption.size => 'file_count $dir',
    };

    final List<Map<String, dynamic>> rows = await db.rawQuery(
      '''
      SELECT f.*,
        (SELECT COUNT(*) FROM folders sub WHERE sub.parent_id = f.id) AS subfolder_count,
        (SELECT COUNT(*) FROM files fi WHERE fi.folder_id = f.id) AS file_count
      FROM folders f
      WHERE ${parentId == null ? 'f.parent_id IS NULL' : 'f.parent_id = ?'}
      ORDER BY $orderClause
      ''',
      parentId == null ? <Object?>[] : <Object?>[parentId],
    );

    return rows.map(FolderItem.fromMap).toList();
  }

  Future<FolderItem?> getFolderById(int id) async {
    final Database db = await database;
    final List<Map<String, dynamic>> rows = await db.rawQuery(
      '''
      SELECT f.*,
        (SELECT COUNT(*) FROM folders sub WHERE sub.parent_id = f.id) AS subfolder_count,
        (SELECT COUNT(*) FROM files fi WHERE fi.folder_id = f.id) AS file_count
      FROM folders f
      WHERE f.id = ?
      LIMIT 1
      ''',
      <Object?>[id],
    );

    if (rows.isEmpty) return null;
    return FolderItem.fromMap(rows.first);
  }

  Future<List<FileItem>> getFiles(
    int folderId, {
    LibrarySortOption sortBy = LibrarySortOption.name,
    bool ascending = true,
  }) async {
    final Database db = await database;
    final String dir = ascending ? 'ASC' : 'DESC';
    final String orderClause = switch (sortBy) {
      LibrarySortOption.name => 'name COLLATE NOCASE $dir',
      LibrarySortOption.date => 'created_at $dir',
      LibrarySortOption.size => 'size_bytes $dir',
    };

    final List<Map<String, dynamic>> rows = await db.query(
      'files',
      where: 'folder_id = ?',
      whereArgs: <Object?>[folderId],
      orderBy: orderClause,
    );

    return rows.map(FileItem.fromMap).toList();
  }

  Future<List<FileItem>> searchFiles(String query) async {
    if (query.trim().isEmpty) return <FileItem>[];
    final Database db = await database;
    final String cleanQuery = '%${query.trim()}%';
    final List<Map<String, dynamic>> rows = await db.query(
      'files',
      where: 'name LIKE ?',
      whereArgs: <Object?>[cleanQuery],
      orderBy: 'name COLLATE NOCASE ASC',
    );

    return rows.map(FileItem.fromMap).toList();
  }

  Future<List<FolderItem>> getBreadcrumbs(int folderId) async {
    final List<FolderItem> chain = <FolderItem>[];
    int? currentId = folderId;

    while (currentId != null) {
      final FolderItem? folder = await getFolderById(currentId);
      if (folder == null) break;
      chain.insert(0, folder);
      currentId = folder.parentId;
    }

    return chain;
  }

  Future<int> insertFolder(String name, {int? parentId}) async {
    final Database db = await database;
    return db.insert('folders', <String, dynamic>{
      'name': name.trim(),
      'parent_id': parentId,
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<int> insertFile(FileItem file) async {
    final Database db = await database;
    return db.insert('files', file.toMap());
  }

  Future<void> deleteFolder(int folderId) async {
    final Database db = await database;
    await db.delete('folders', where: 'id = ?', whereArgs: <Object?>[folderId]);
  }

  Future<void> deleteFile(int fileId) async {
    final Database db = await database;
    await db.delete('files', where: 'id = ?', whereArgs: <Object?>[fileId]);
  }

  Future<void> seedDummyData({bool force = false}) async {
    final Database db = await database;
    if (force) {
      await db.delete('files');
      await db.delete('folders');
    }

    final int now = DateTime.now().millisecondsSinceEpoch;

    // Helper closure
    Future<int> addF(String name, [int? parentId]) {
      return db.insert('folders', <String, dynamic>{
        'name': name,
        'parent_id': parentId,
        'created_at': now,
      });
    }

    Future<int> addM(
      int folderId,
      String name,
      String ext,
      String kind,
      int size,
      int durMs,
      String path,
    ) {
      return db.insert('files', <String, dynamic>{
        'folder_id': folderId,
        'name': name,
        'extension': ext,
        'kind': kind,
        'size_bytes': size,
        'duration_ms': durMs,
        'path': path,
        'created_at': now,
        'modified_at': now,
      });
    }

    // Root Folder 1: Music
    final int musicId = await addF('Music');
    final int synthId = await addF('Synthwave & Retrowave', musicId);
    await addM(synthId, 'Resonance - Echoes', 'flac', 'audio', 32925286, 212000, '/Music/Synthwave/Resonance - Echoes.flac');
    await addM(synthId, 'Cyberpunk Night Drive', 'mp3', 'audio', 9017753, 235000, '/Music/Synthwave/Cyberpunk Night Drive.mp3');
    await addM(synthId, 'Neon Horizon', 'mp3', 'audio', 6501171, 184000, '/Music/Synthwave/Neon Horizon.mp3');
    await addM(synthId, 'Electric Dreams in Neon', 'mp3', 'audio', 8178892, 205000, '/Music/Synthwave/Electric Dreams in Neon.mp3');

    final int rockId = await addF('Rock Classics', musicId);
    await addM(rockId, 'Stairway to the Stars', 'mp3', 'audio', 10276044, 342000, '/Music/Rock Classics/Stairway to the Stars.mp3');
    await addM(rockId, 'Hotel California (Live Master)', 'flac', 'audio', 44145049, 390000, '/Music/Rock Classics/Hotel California (Live Master).flac');
    await addM(rockId, 'Bohemian Symphony in F', 'wav', 'audio', 61551411, 355000, '/Music/Rock Classics/Bohemian Symphony in F.wav');

    final int lofiId = await addF('Lo-Fi Chill Beats', musicId);
    await addM(lofiId, 'Rainy Afternoon in Shibuya', 'mp3', 'audio', 5662310, 150000, '/Music/Lo-Fi Chill Beats/Rainy Afternoon in Shibuya.mp3');
    await addM(lofiId, 'Midnight Coffee & Flutter Code', 'mp3', 'audio', 7444889, 198000, '/Music/Lo-Fi Chill Beats/Midnight Coffee & Flutter Code.mp3');
    await addM(lofiId, 'Sunset Boulevard Walk', 'mp3', 'audio', 5138022, 132000, '/Music/Lo-Fi Chill Beats/Sunset Boulevard Walk.mp3');

    await addM(musicId, 'Library Welcome Theme', 'mp3', 'audio', 3355443, 90000, '/Music/Library Welcome Theme.mp3');

    // Root Folder 2: Videos & Movies
    final int videosId = await addF('Videos & Movies');
    final int filmsId = await addF('Feature Films', videosId);
    await addM(filmsId, 'Cosmic Odyssey (2025) 4K', 'mp4', 'video', 1520435200, 7420000, '/Videos/Feature Films/Cosmic Odyssey (2025) 4K.mp4');
    await addM(filmsId, 'The Last Horizon (Remastered)', 'mkv', 'video', 1027604480, 6100000, '/Videos/Feature Films/The Last Horizon.mkv');
    await addM(filmsId, 'Deep Ocean Secrets', 'mp4', 'video', 650117120, 3240000, '/Videos/Feature Films/Deep Ocean Secrets.mp4');

    final int tutsId = await addF('Tutorials & Tech Talks', videosId);
    await addM(tutsId, 'Flutter Desktop Mastery Ep 1', 'mp4', 'video', 193986560, 1450000, '/Videos/Tutorials/Flutter Desktop Mastery Ep 1.mp4');
    await addM(tutsId, 'SQLite Architecture & WAL Deep Dive', 'mp4', 'video', 241172480, 1920000, '/Videos/Tutorials/SQLite Architecture & WAL Deep Dive.mp4');
    await addM(tutsId, 'Modern Linux Audio Subsystems', 'mp4', 'video', 167772160, 1150000, '/Videos/Tutorials/Modern Linux Audio Subsystems.mp4');

    await addM(videosId, '4K Nature Drone Reel', 'mp4', 'video', 117440512, 65000, '/Videos/4K Nature Drone Reel.mp4');

    // Root Folder 3: Podcasts
    final int podcastsId = await addF('Podcasts');
    final int showId = await addF('The MPx Audio Show', podcastsId);
    await addM(showId, 'Ep 01 - Building Fast Native Media Players', 'mp3', 'audio', 56832819, 3120000, '/Podcasts/The MPx Audio Show/Ep 01.mp3');
    await addM(showId, 'Ep 02 - State Management & Low Bandwidth Design', 'mp3', 'audio', 64801996, 3450000, '/Podcasts/The MPx Audio Show/Ep 02.mp3');

    final int scienceId = await addF('Science & Cosmos', podcastsId);
    await addM(scienceId, 'Ep 14 - Black Holes and Quantum Gravity', 'mp3', 'audio', 50331648, 2760000, '/Podcasts/Science & Cosmos/Ep 14.mp3');

    // Root Folder 4: Downloads
    final int downloadsId = await addF('Downloads');
    final int soundFxId = await addF('Sound FX & Stems', downloadsId);
    await addM(soundFxId, 'Analog Modular Synth Sweep', 'wav', 'audio', 5033164, 15000, '/Downloads/Sound FX & Stems/Analog Modular Synth Sweep.wav');
    await addM(soundFxId, 'Vinyl Crackle 33RPM Atmosphere', 'wav', 'audio', 12897484, 60000, '/Downloads/Sound FX & Stems/Vinyl Crackle 33RPM Atmosphere.wav');

    await addM(downloadsId, '1080p 60FPS Video Benchmark', 'mp4', 'video', 88080384, 35000, '/Downloads/1080p 60FPS Video Benchmark.mp4');
  }

  Future<void> close() async {
    final Database? db = _database;
    _database = null;
    _opening = null;
    await db?.close();
  }
}
