import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import '../services/logger_service.dart';
import 'operations/video_operations.dart';
import 'operations/folder_operations.dart';
import 'operations/favorites_operations.dart';
import 'operations/watch_history_operations.dart';
import 'operations/library_index_operations.dart';
import 'operations/download_operations.dart';

/// Main database class - MPx Player
/// Manages video library, folders, favorites, and watch history with SQLite
class AppDatabase
    with
        VideoDatabaseOperations,
        FolderDatabaseOperations,
        FavoritesDatabaseOperations,
        WatchHistoryOperations,
        LibraryIndexDatabaseOperations,
        DownloadDatabaseOperations {
  static final AppDatabase _instance = AppDatabase._internal();
  static Database? _database;

  factory AppDatabase() => _instance;
  AppDatabase._internal();

  static const String _databaseName = 'mpx_player.db';
  static const int _databaseVersion = 6;

  /// Get database instance (singleton)
  @override
  Future<Database> get database async => _database ??= await _initDatabase();

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

    // Library Index Metadata table
    await db.execute('''
      CREATE TABLE library_index_metadata (
        root_path TEXT PRIMARY KEY,
        indexed_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Videos table
    await db.execute('''
      CREATE TABLE videos (
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

    // Folders table
    await db.execute('''
      CREATE TABLE folders (
        path TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        video_count INTEGER NOT NULL DEFAULT 0,
        thumbnail_path TEXT,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Favorites table
    await db.execute('''
      CREATE TABLE favorites (
        video_id TEXT PRIMARY KEY,
        added_at INTEGER NOT NULL,
        FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
      )
    ''');

    // Watch history table
    await db.execute('''
      CREATE TABLE watch_history (
        video_id TEXT PRIMARY KEY,
        position_ms INTEGER NOT NULL DEFAULT 0,
        duration_ms INTEGER NOT NULL DEFAULT 0,
        last_played_at INTEGER NOT NULL,
        completion_percent INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE downloads (
        id TEXT PRIMARY KEY,
        video_id TEXT,
        url TEXT NOT NULL,
        title TEXT NOT NULL,
        save_path TEXT,
        format_selector TEXT,
        status TEXT NOT NULL,
        progress REAL NOT NULL DEFAULT 0.0,
        added_at INTEGER NOT NULL,
        completed_at INTEGER,
        error_message TEXT
      )
    ''');

    // Create indexes for performance
    await db.execute('CREATE INDEX idx_videos_folder ON videos(folder_path)');
    await db.execute('CREATE INDEX idx_videos_date ON videos(date_added DESC)');
    await db.execute('CREATE INDEX idx_videos_title ON videos(title)');
    await db.execute(
        'CREATE INDEX idx_history_played ON watch_history(last_played_at DESC)');
    await db.execute('CREATE INDEX idx_downloads_status ON downloads(status)');
    await db.execute(
        'CREATE INDEX idx_downloads_added ON downloads(added_at DESC)');

    AppLogger.i('Database tables created successfully');
  }

  /// Handle database upgrades
  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    AppLogger.i('Upgrading database from $oldVersion to $newVersion');

    if (oldVersion < 2) {
      AppLogger.i('Migrating to version 2: Adding watch_history table');
      await db.execute('''
        CREATE TABLE watch_history (
          video_id TEXT PRIMARY KEY,
          position_ms INTEGER NOT NULL DEFAULT 0,
          duration_ms INTEGER NOT NULL DEFAULT 0,
          last_played_at INTEGER NOT NULL,
          completion_percent INTEGER NOT NULL DEFAULT 0,
          FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
        )
      ''');
      await db.execute(
          'CREATE INDEX idx_history_played ON watch_history(last_played_at DESC)');
      AppLogger.i('Watch history table created');
    }

    if (oldVersion < 3) {
      AppLogger.i('Migrating to version 3: Adding downloads table');
      await db.execute('''
        CREATE TABLE downloads (
          id TEXT PRIMARY KEY,
          video_id TEXT,
          url TEXT NOT NULL,
          title TEXT NOT NULL,
          save_path TEXT,
          format_selector TEXT,
          status TEXT NOT NULL,
          progress REAL NOT NULL DEFAULT 0.0,
          added_at INTEGER NOT NULL,
          completed_at INTEGER,
          error_message TEXT
        )
      ''');
      await db
          .execute('CREATE INDEX idx_downloads_status ON downloads(status)');
      await db.execute(
          'CREATE INDEX idx_downloads_added ON downloads(added_at DESC)');
      AppLogger.i('Downloads table created');
    }

    if (oldVersion < 4) {
      AppLogger.i(
          'Migrating to version 4: Adding subtitle_paths to watch_history');
      await db.execute(
          'ALTER TABLE watch_history ADD COLUMN subtitle_paths TEXT DEFAULT ""');
      AppLogger.i('subtitle_paths column added');
    }

    if (oldVersion < 5) {
      AppLogger.i(
          'Migrating to version 5: Adding selected_subtitle_track to watch_history');
      await db.execute(
          'ALTER TABLE watch_history ADD COLUMN selected_subtitle_track TEXT DEFAULT ""');
      AppLogger.i('selected_subtitle_track column added');
    }

    if (oldVersion < 6) {
      AppLogger.i(
          'Migrating to version 6: Adding selected_audio_track to watch_history');
      await db.execute(
          'ALTER TABLE watch_history ADD COLUMN selected_audio_track TEXT DEFAULT ""');
      AppLogger.i('selected_audio_track column added');
    }
  }

  /// Close database connection
  Future<void> close() async {
    if (_database != null) {
      await _database!.close();
      _database = null;
      AppLogger.i('Database connection closed');
    }
  }

  /// Delete all data (for testing/debugging)
  Future<void> deleteAllData() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('watch_history');
      await txn.delete('favorites');
      await txn.delete('videos');
      await txn.delete('folders');
      await txn.delete('downloads');
    });
    AppLogger.i('All database data deleted');
  }
}
