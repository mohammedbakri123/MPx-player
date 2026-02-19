import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import '../../features/library/domain/entities/video_file.dart';
import '../../features/library/domain/entities/video_folder.dart';
import '../services/logger_service.dart';

/// Database service for MPx Player
/// Manages video library, folders, and favorites with SQLite
class AppDatabase {
  static final AppDatabase _instance = AppDatabase._internal();
  static Database? _database;

  factory AppDatabase() => _instance;
  AppDatabase._internal();

  static const String _databaseName = 'mpx_player.db';
  static const int _databaseVersion = 1;

  // Table names
  static const String _videosTable = 'videos';
  static const String _foldersTable = 'folders';
  static const String _favoritesTable = 'favorites';

  /// Get database instance (singleton)
  Future<Database> get database async {
    _database ??= await _initDatabase();
    return _database!;
  }

  /// Initialize database
  Future<Database> _initDatabase() async {
    final databasesPath = await getDatabasesPath();
    final path = join(databasesPath, _databaseName);

    AppLogger.i('Initializing database at: $path');

    return await openDatabase(
      path,
      version: _databaseVersion,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  /// Create tables on first launch
  Future<void> _onCreate(Database db, int version) async {
    AppLogger.i('Creating database tables (version $version)');

    // Videos table
    await db.execute('''
      CREATE TABLE $_videosTable (
        id TEXT PRIMARY KEY,
        path TEXT UNIQUE NOT NULL,
        title TEXT NOT NULL,
        folder_path TEXT NOT NULL,
        folder_name TEXT NOT NULL,
        size INTEGER NOT NULL,
        duration INTEGER NOT NULL,
        date_added INTEGER NOT NULL,
        width INTEGER,
        height INTEGER,
        thumbnail_path TEXT,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Folders table (denormalized for performance)
    await db.execute('''
      CREATE TABLE $_foldersTable (
        path TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        video_count INTEGER NOT NULL DEFAULT 0,
        thumbnail_path TEXT,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Favorites table
    await db.execute('''
      CREATE TABLE $_favoritesTable (
        video_id TEXT PRIMARY KEY,
        added_at INTEGER NOT NULL,
        FOREIGN KEY (video_id) REFERENCES $_videosTable(id) ON DELETE CASCADE
      )
    ''');

    // Create indexes for performance
    await db.execute(
        'CREATE INDEX idx_videos_folder ON $_videosTable(folder_path)');
    await db.execute(
        'CREATE INDEX idx_videos_date ON $_videosTable(date_added DESC)');
    await db.execute('CREATE INDEX idx_videos_title ON $_videosTable(title)');

    AppLogger.i('Database tables created successfully');
  }

  /// Handle database upgrades
  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    AppLogger.i('Upgrading database from $oldVersion to $newVersion');
    // Handle future schema migrations here
  }

  /// Close database connection
  Future<void> close() async {
    if (_database != null) {
      await _database!.close();
      _database = null;
      AppLogger.i('Database connection closed');
    }
  }

  // ==================== VIDEO OPERATIONS ====================

  /// Insert or update a video
  Future<void> insertVideo(VideoFile video) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.insert(
      _videosTable,
      {
        'id': video.id,
        'path': video.path,
        'title': video.title,
        'folder_path': video.folderPath,
        'folder_name': video.folderName,
        'size': video.size,
        'duration': video.duration,
        'date_added': video.dateAdded.millisecondsSinceEpoch,
        'width': video.width,
        'height': video.height,
        'thumbnail_path': video.thumbnailPath,
        'updated_at': now,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Insert multiple videos in a transaction
  Future<void> insertVideos(List<VideoFile> videos) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.transaction((txn) async {
      final batch = txn.batch();

      for (final video in videos) {
        batch.insert(
          _videosTable,
          {
            'id': video.id,
            'path': video.path,
            'title': video.title,
            'folder_path': video.folderPath,
            'folder_name': video.folderName,
            'size': video.size,
            'duration': video.duration,
            'date_added': video.dateAdded.millisecondsSinceEpoch,
            'width': video.width,
            'height': video.height,
            'thumbnail_path': video.thumbnailPath,
            'updated_at': now,
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }

      await batch.commit(noResult: true);
    });

    AppLogger.i('Inserted ${videos.length} videos into database');
  }

  /// Get all videos
  Future<List<VideoFile>> getAllVideos() async {
    final db = await database;
    final maps = await db.query(_videosTable);

    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Get videos by folder path
  Future<List<VideoFile>> getVideosByFolder(String folderPath) async {
    final db = await database;
    final maps = await db.query(
      _videosTable,
      where: 'folder_path = ?',
      whereArgs: [folderPath],
      orderBy: 'date_added DESC',
    );

    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Get video by ID
  Future<VideoFile?> getVideoById(String id) async {
    final db = await database;
    final maps = await db.query(
      _videosTable,
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );

    if (maps.isEmpty) return null;
    return _videoFromMap(maps.first);
  }

  /// Search videos by title
  Future<List<VideoFile>> searchVideos(String query) async {
    final db = await database;
    final maps = await db.query(
      _videosTable,
      where: 'title LIKE ?',
      whereArgs: ['%$query%'],
      orderBy: 'title ASC',
    );

    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Delete a video
  Future<void> deleteVideo(String id) async {
    final db = await database;
    await db.delete(
      _videosTable,
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  /// Delete all videos
  Future<void> deleteAllVideos() async {
    final db = await database;
    await db.delete(_videosTable);
    AppLogger.i('All videos deleted from database');
  }

  /// Get video count
  Future<int> getVideoCount() async {
    final db = await database;
    final result =
        await db.rawQuery('SELECT COUNT(*) as count FROM $_videosTable');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  // ==================== FOLDER OPERATIONS ====================

  /// Insert or update a folder
  Future<void> insertFolder(VideoFolder folder) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.insert(
      _foldersTable,
      {
        'path': folder.path,
        'name': folder.name,
        'video_count': folder.videoCount,
        'thumbnail_path':
            folder.videos.isNotEmpty ? folder.videos.first.thumbnailPath : null,
        'updated_at': now,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Insert multiple folders in a transaction
  Future<void> insertFolders(List<VideoFolder> folders) async {
    final db = await database;
    final now = DateTime.now().millisecondsSinceEpoch;

    await db.transaction((txn) async {
      final batch = txn.batch();

      for (final folder in folders) {
        batch.insert(
          _foldersTable,
          {
            'path': folder.path,
            'name': folder.name,
            'video_count': folder.videoCount,
            'thumbnail_path': folder.videos.isNotEmpty
                ? folder.videos.first.thumbnailPath
                : null,
            'updated_at': now,
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }

      await batch.commit(noResult: true);
    });

    AppLogger.i('Inserted ${folders.length} folders into database');
  }

  /// Get all folders
  Future<List<VideoFolder>> getAllFolders() async {
    final db = await database;
    final folderMaps = await db.query(_foldersTable);

    final folders = <VideoFolder>[];
    for (final folderMap in folderMaps) {
      final folderPath = folderMap['path'] as String;
      final videos = await getVideosByFolder(folderPath);

      folders.add(VideoFolder(
        path: folderPath,
        name: folderMap['name'] as String,
        videos: videos,
      ));
    }

    return folders;
  }

  /// Delete a folder
  Future<void> deleteFolder(String path) async {
    final db = await database;
    await db.delete(
      _foldersTable,
      where: 'path = ?',
      whereArgs: [path],
    );
  }

  /// Delete all folders
  Future<void> deleteAllFolders() async {
    final db = await database;
    await db.delete(_foldersTable);
  }

  /// Get folder count
  Future<int> getFolderCount() async {
    final db = await database;
    final result =
        await db.rawQuery('SELECT COUNT(*) as count FROM $_foldersTable');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  // ==================== FAVORITES OPERATIONS ====================

  /// Add video to favorites
  Future<bool> addFavorite(String videoId) async {
    try {
      final db = await database;
      final now = DateTime.now().millisecondsSinceEpoch;

      await db.insert(
        _favoritesTable,
        {
          'video_id': videoId,
          'added_at': now,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );

      return true;
    } catch (e) {
      AppLogger.e('Error adding favorite: $e');
      return false;
    }
  }

  /// Remove video from favorites
  Future<bool> removeFavorite(String videoId) async {
    try {
      final db = await database;
      await db.delete(
        _favoritesTable,
        where: 'video_id = ?',
        whereArgs: [videoId],
      );
      return true;
    } catch (e) {
      AppLogger.e('Error removing favorite: $e');
      return false;
    }
  }

  /// Toggle favorite status
  Future<bool> toggleFavorite(String videoId) async {
    final isFav = await isFavorite(videoId);
    if (isFav) {
      return await removeFavorite(videoId);
    } else {
      return await addFavorite(videoId);
    }
  }

  /// Get all favorite videos
  Future<List<VideoFile>> getFavorites() async {
    final db = await database;
    final maps = await db.rawQuery('''
      SELECT v.* FROM $_videosTable v
      INNER JOIN $_favoritesTable f ON v.id = f.video_id
      ORDER BY f.added_at DESC
    ''');

    return maps.map((map) => _videoFromMap(map)).toList();
  }

  /// Check if video is favorite
  Future<bool> isFavorite(String videoId) async {
    final db = await database;
    final maps = await db.query(
      _favoritesTable,
      where: 'video_id = ?',
      whereArgs: [videoId],
      limit: 1,
    );
    return maps.isNotEmpty;
  }

  /// Clear all favorites
  Future<void> clearFavorites() async {
    final db = await database;
    await db.delete(_favoritesTable);
  }

  /// Get favorite count
  Future<int> getFavoriteCount() async {
    final db = await database;
    final result =
        await db.rawQuery('SELECT COUNT(*) as count FROM $_favoritesTable');
    return Sqflite.firstIntValue(result) ?? 0;
  }

  // ==================== HELPER METHODS ====================

  VideoFile _videoFromMap(Map<String, dynamic> map) {
    return VideoFile(
      id: map['id'] as String,
      path: map['path'] as String,
      title: map['title'] as String,
      folderPath: map['folder_path'] as String,
      folderName: map['folder_name'] as String,
      size: map['size'] as int,
      duration: map['duration'] as int,
      dateAdded: DateTime.fromMillisecondsSinceEpoch(map['date_added'] as int),
      width: map['width'] as int?,
      height: map['height'] as int?,
      thumbnailPath: map['thumbnail_path'] as String?,
    );
  }

  /// Delete all data (for testing/debugging)
  Future<void> deleteAllData() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete(_favoritesTable);
      await txn.delete(_videosTable);
      await txn.delete(_foldersTable);
    });
    AppLogger.i('All database data deleted');
  }
}
