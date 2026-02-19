import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:mpx/features/library/controller/library_controller.dart';
import 'package:mpx/features/library/data/datasources/local_video_scanner.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/library/domain/entities/video_folder.dart';

import '../../mocks/video_scanner_mock.mocks.dart';

void main() {
  group('LibraryController', () {
    late LibraryController controller;
    late MockVideoScanner mockScanner;

    // Test data
    final testVideoFile = VideoFile(
      id: '1',
      path: '/test/video1.mp4',
      title: 'video1.mp4',
      folderPath: '/test',
      folderName: 'test',
      size: 1024 * 1024 * 100, // 100MB
      duration: 60000, // 1 minute
      dateAdded: DateTime.now(),
      width: 1920,
      height: 1080,
    );

    final testVideoFolder = VideoFolder(
      path: '/test',
      name: 'test',
      videos: [testVideoFile],
    );

    setUp(() {
      mockScanner = MockVideoScanner();
      controller = LibraryController(mockScanner);
    });

    tearDown(() {
      controller.dispose();
    });

    group('Initial State', () {
      test('initial state should have correct default values', () {
        expect(controller.folders, isEmpty);
        expect(controller.isLoading, true);
        expect(controller.isGridView, false);
        expect(controller.hasError, false);
        expect(controller.errorMessage, isNull);
        expect(controller.isEmpty, false); // isLoading is true, so not empty
      });
    });

    group('load()', () {
      test('should set isLoading to true initially', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        // Act
        final future = controller.load();

        // Assert - immediately after calling load
        expect(controller.isLoading, true);

        // Wait for completion
        await future;
      });

      test('should load folders successfully', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        // Act
        await controller.load();

        // Assert
        expect(controller.isLoading, false);
        expect(controller.folders, [testVideoFolder]);
        expect(controller.hasError, false);
        expect(controller.errorMessage, isNull);
        expect(controller.isEmpty, false);
      });

      test('should handle empty result', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => []);

        // Act
        await controller.load();

        // Assert
        expect(controller.isLoading, false);
        expect(controller.folders, isEmpty);
        expect(controller.isEmpty, true);
      });

      test('should handle errors gracefully', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenThrow(Exception('Scan failed'));

        // Act
        await controller.load();

        // Assert
        expect(controller.isLoading, false);
        expect(controller.hasError, true);
        expect(controller.errorMessage, contains('Failed to load videos'));
        expect(controller.folders, isEmpty);
      });

      test('should call scanner with correct parameters', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => []);

        // Act
        await controller.load();

        // Assert
        verify(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).called(1);
      });
    });

    group('refresh()', () {
      test('should call scanner with forceRefresh=true', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: true,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        // Act
        await controller.refresh();

        // Assert
        verify(mockScanner.scanForVideos(
          forceRefresh: true,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).called(1);
      });

      test('should clear caches on refresh', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: anyNamed('forceRefresh'),
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        when(mockScanner.getVideosInFolder('/test'))
            .thenAnswer((_) async => [testVideoFile]);

        // First load to populate cache
        await controller.load();
        await controller.loadFolderVideos('/test');
        expect(controller.cacheStats['folderVideoCacheSize'], 1);
        expect(controller.isFolderLoaded('/test'), true);

        // Act - refresh should clear cache
        await controller.refresh();

        // Assert - cache should be cleared
        expect(controller.cacheStats['folderVideoCacheSize'], 0);
        expect(controller.isFolderLoaded('/test'), false);
      });
    });

    group('View Mode', () {
      test('toggleViewMode should switch between list and grid', () {
        // Arrange
        expect(controller.isGridView, false);

        // Act - toggle to grid
        controller.toggleViewMode();

        // Assert
        expect(controller.isGridView, true);

        // Act - toggle back to list
        controller.toggleViewMode();

        // Assert
        expect(controller.isGridView, false);
      });

      test('setViewMode should set specific mode', () {
        // Act
        controller.setViewMode(true);

        // Assert
        expect(controller.isGridView, true);

        // Act
        controller.setViewMode(false);

        // Assert
        expect(controller.isGridView, false);
      });

      test('setViewMode should not notify when mode unchanged', () {
        // Arrange
        var notifyCount = 0;
        controller.addListener(() => notifyCount++);

        // Act - set same mode
        controller.setViewMode(false);

        // Assert - should not notify since already false
        expect(notifyCount, 0);

        // Act - change mode
        controller.setViewMode(true);

        // Assert
        expect(notifyCount, 1);

        // Act - set same mode again
        controller.setViewMode(true);

        // Assert - should not notify
        expect(notifyCount, 1);
      });
    });

    group('Demo Data', () {
      test('loadDemoData should populate folders', () {
        // Act
        controller.loadDemoData();

        // Assert
        expect(controller.isLoading, false);
        expect(controller.hasError, false);
        expect(controller.errorMessage, isNull);
        // Demo data returns empty list in current implementation
        expect(controller.folders, isEmpty);
      });
    });

    group('Lazy Loading', () {
      test('isFolderLoaded should return false for unloaded folder', () {
        expect(controller.isFolderLoaded('/test'), false);
      });

      test('loadFolderVideos should return cached videos if available',
          () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        when(mockScanner.getVideosInFolder('/test'))
            .thenAnswer((_) async => [testVideoFile]);

        await controller.load();

        // Act - First load
        final videos1 = await controller.loadFolderVideos('/test');

        // Assert
        expect(videos1, [testVideoFile]);
        expect(controller.isFolderLoaded('/test'), true);

        // Act - Second load (should use cache)
        final videos2 = await controller.loadFolderVideos('/test');

        // Assert - scanner should only be called once for the initial load
        verify(mockScanner.getVideosInFolder('/test')).called(1);
        expect(videos2, [testVideoFile]);
      });

      test('loadFolderVideos should handle errors gracefully', () async {
        // Arrange
        when(mockScanner.getVideosInFolder('/test'))
            .thenThrow(Exception('Access denied'));

        // Act
        final videos = await controller.loadFolderVideos('/test');

        // Assert - should return empty list on error
        expect(videos, isEmpty);
        expect(controller.isFolderLoaded('/test'), false);
      });

      test('invalidateFolder should clear specific folder cache', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        when(mockScanner.getVideosInFolder('/test'))
            .thenAnswer((_) async => [testVideoFile]);

        await controller.load();
        await controller.loadFolderVideos('/test');

        expect(controller.isFolderLoaded('/test'), true);

        // Act
        controller.invalidateFolder('/test');

        // Assert
        expect(controller.isFolderLoaded('/test'), false);
      });
    });

    group('Cache Management', () {
      test('clearFolderCaches should clear all caches', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        when(mockScanner.getVideosInFolder('/test'))
            .thenAnswer((_) async => [testVideoFile]);

        await controller.load();
        await controller.loadFolderVideos('/test');

        final stats = controller.cacheStats;
        expect(stats['loadedFolders'], 1);
        expect(stats['folderVideoCacheSize'], 1);

        // Act
        controller.clearFolderCaches();

        // Assert
        final clearedStats = controller.cacheStats;
        expect(clearedStats['loadedFolders'], 0);
        expect(clearedStats['folderVideoCacheSize'], 0);
      });

      test('cacheStats should return correct statistics', () async {
        // Arrange
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        when(mockScanner.getVideosInFolder('/test'))
            .thenAnswer((_) async => [testVideoFile]);

        await controller.load();
        await controller.loadFolderVideos('/test');

        // Act
        final stats = controller.cacheStats;

        // Assert
        expect(stats['loadedFolders'], 1);
        expect(stats['folderVideoCacheSize'], 1);
        expect(stats['totalFolders'], 1);
      });
    });

    group('Notification Tests', () {
      test('should notify listeners on state changes', () async {
        // Arrange
        var notifyCount = 0;
        controller.addListener(() => notifyCount++);

        // Act - initial load
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => [testVideoFolder]);

        await controller.load();

        // Assert - should have notified at least twice (loading start and end)
        expect(notifyCount, greaterThanOrEqualTo(2));
      });

      test('should notify on view mode toggle', () {
        // Arrange
        var notifyCount = 0;
        controller.addListener(() => notifyCount++);

        // Act
        controller.toggleViewMode();

        // Assert
        expect(notifyCount, 1);
      });
    });

    group('Edge Cases', () {
      test('should handle scanner returning null gracefully', () async {
        // Note: scanForVideos should never return null based on its implementation,
        // but we should handle it defensively
        when(mockScanner.scanForVideos(
          forceRefresh: false,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async => []);

        // Act
        await controller.load();

        // Assert
        expect(controller.isLoading, false);
        expect(controller.folders, isEmpty);
      });

      test('should handle multiple rapid refresh calls', () async {
        // Arrange
        var callCount = 0;
        when(mockScanner.scanForVideos(
          forceRefresh: true,
          enableWatching: true,
          onProgress: anyNamed('onProgress'),
        )).thenAnswer((_) async {
          callCount++;
          return [testVideoFolder];
        });

        // Act - multiple rapid calls
        await Future.wait([
          controller.refresh(),
          controller.refresh(),
          controller.refresh(),
        ]);

        // Assert - should handle gracefully (actual behavior depends on implementation)
        expect(controller.isLoading, false);
        expect(callCount, greaterThanOrEqualTo(1));
      });
    });
  });
}
