import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:mpx/features/library/controller/library_controller.dart';
import 'package:mpx/features/library/data/library_database.dart';
import 'package:mpx/features/library/models/file_item.dart';
import 'package:mpx/features/library/models/folder_item.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
  });

  group('LibraryDatabase (SQLite Dummy Data)', () {
    late LibraryDatabase db;

    setUp(() {
      db = LibraryDatabase(
        factory: databaseFactoryFfi,
        path: inMemoryDatabasePath,
      );
    });

    tearDown(() async {
      await db.close();
    });

    test('initializes and seeds dummy folders and files', () async {
      final List<FolderItem> rootFolders = await db.getFolders();
      expect(rootFolders, isNotEmpty);
      expect(
        rootFolders.map((FolderItem f) => f.name),
        containsAll(<String>['Music', 'Videos & Movies', 'Podcasts', 'Downloads']),
      );

      // Verify Music subfolders
      final FolderItem music =
          rootFolders.firstWhere((FolderItem f) => f.name == 'Music');
      final List<FolderItem> musicSubfolders =
          await db.getFolders(parentId: music.id);
      expect(musicSubfolders, isNotEmpty);
      expect(
        musicSubfolders.map((FolderItem f) => f.name),
        contains('Synthwave & Retrowave'),
      );

      // Verify files in subfolder
      final FolderItem synth = musicSubfolders
          .firstWhere((FolderItem f) => f.name == 'Synthwave & Retrowave');
      final List<FileItem> synthFiles = await db.getFiles(synth.id);
      expect(synthFiles, isNotEmpty);
      expect(synthFiles.first.isAudio, isTrue);
      expect(synthFiles.first.formattedDuration, isNot('--:--'));
      expect(synthFiles.first.formattedSize, contains('MB'));
    });

    test('searchFiles finds files across folders', () async {
      final List<FileItem> results = await db.searchFiles('Synth');
      expect(results, isNotEmpty);
      expect(results.any((FileItem f) => f.name.contains('Synth')), isTrue);
    });

    test('breadcrumbs build accurate hierarchy', () async {
      final List<FolderItem> rootFolders = await db.getFolders();
      final FolderItem music =
          rootFolders.firstWhere((FolderItem f) => f.name == 'Music');
      final List<FolderItem> musicSubfolders =
          await db.getFolders(parentId: music.id);
      final FolderItem rock =
          musicSubfolders.firstWhere((FolderItem f) => f.name == 'Rock Classics');

      final List<FolderItem> crumbs = await db.getBreadcrumbs(rock.id);
      expect(crumbs.length, equals(2));
      expect(crumbs[0].name, equals('Music'));
      expect(crumbs[1].name, equals('Rock Classics'));
    });
  });

  group('LibraryController', () {
    late LibraryDatabase db;
    late LibraryController controller;

    setUp(() {
      db = LibraryDatabase(
        factory: databaseFactoryFfi,
        path: inMemoryDatabasePath,
      );
      controller = LibraryController(database: db);
    });

    tearDown(() async {
      await db.close();
    });

    test('loads root folders and navigates into a folder', () async {
      await controller.load();
      expect(controller.isRoot, isTrue);
      expect(controller.folders, isNotEmpty);

      final FolderItem first = controller.folders.first;
      await controller.openFolder(first);

      expect(controller.isRoot, isFalse);
      expect(controller.currentFolder?.id, equals(first.id));
      expect(controller.breadcrumbs.length, equals(1));

      // Navigate back up
      await controller.navigateUp();
      expect(controller.isRoot, isTrue);
      expect(controller.currentFolder, isNull);
    });

    test('filters files by kind', () async {
      await controller.load();
      final FolderItem videos = controller.folders
          .firstWhere((FolderItem f) => f.name == 'Videos & Movies');
      await controller.openFolder(videos);

      controller.setFilterKind(FileKind.video);
      expect(controller.filterKind, equals(FileKind.video));

      controller.setFilterKind(FileKind.audio);
      expect(controller.filterKind, equals(FileKind.audio));
    });

    test('searches files', () async {
      await controller.setSearchQuery('Odyssey');
      expect(controller.searchResults, isNotEmpty);
      expect(
        controller.searchResults.first.name,
        contains('Cosmic Odyssey'),
      );
    });
  });
}
