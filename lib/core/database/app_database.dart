import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import '../services/logger_service.dart';
import 'operations/video_operations.dart';
import 'operations/folder_operations.dart';
import 'operations/favorites_operations.dart';

/// Main database class - MPx Player
/// Manages video library, folders, and favorites with SQLite
class AppDatabase with VideoDatabaseOperations, FolderDatabaseOperations, FavoritesDatabaseOperations {
  static final AppDatabase _instance = AppDatabase._internal();
  static Database? _database;

  factory AppDatabase() => _instance;
  AppDatabase._internal();

  static const String _databaseName = 'mpx_player.db';
  static const int _databaseVersion = 1;

  /// Get database instance (singleton)
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

    // Create indexes for performance
    await db.execute('CREATE INDEX idx_videos_folder ON videos(folder_path)');
    await db.execute('CREATE INDEX idx_videos_date ON videos(date_added DESC)');
    await db.execute('CREATE INDEX idx_videos_title ON videos(title)');

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

  /// Delete all data (for testing/debugging)
  Future<void> deleteAllData() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('favorites');
      await txn.delete('videos');
      await txn.delete('folders');
    });
    AppLogger.i('All database data deleted');
  }
}
