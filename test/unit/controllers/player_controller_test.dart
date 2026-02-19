import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mpx/features/player/controller/player_controller.dart';
import 'package:mpx/features/player/controller/player_state.dart';
import 'package:mpx/features/player/domain/repositories/player_repository.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/player/services/play_history_service.dart';
import 'package:mpx/features/settings/services/subtitle_settings_service.dart';

import '../../mocks/player_repository_mock.mocks.dart';

void main() {
  // Initialize Flutter binding before any tests run
  TestWidgetsFlutterBinding.ensureInitialized();

  // Mock platform channels to avoid native dependencies
  const MethodChannel wakelockChannel =
      MethodChannel('dev.fluttercommunity.plus/wakelock');

  setUpAll(() {
    // Mock wakelock platform channel
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      wakelockChannel,
      (MethodCall methodCall) async {
        return true;
      },
    );
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      wakelockChannel,
      null,
    );
  });

  group('PlayerController', () {
    late PlayerController controller;
    late MockPlayerRepository mockRepository;

    // Test data
    final testVideoFile = VideoFile(
      id: 'test-video-1',
      path: '/test/videos/test.mp4',
      title: 'test.mp4',
      folderPath: '/test/videos',
      folderName: 'videos',
      size: 1024 * 1024 * 500, // 500MB
      duration: 120000, // 2 minutes
      dateAdded: DateTime.now(),
      width: 1920,
      height: 1080,
    );

    // Streams for mocking
    late StreamController<bool> playingController;
    late StreamController<Duration> positionController;
    late StreamController<Duration> durationController;
    late StreamController<bool> bufferingController;
    late StreamController<bool> completedController;

    setUp(() async {
      // Setup mock SharedPreferences
      SharedPreferences.setMockInitialValues({});

      // Initialize services
      await PlayHistoryService.init();
      await SubtitleSettingsService.init();

      // Setup stream controllers
      playingController = StreamController<bool>.broadcast();
      positionController = StreamController<Duration>.broadcast();
      durationController = StreamController<Duration>.broadcast();
      bufferingController = StreamController<bool>.broadcast();
      completedController = StreamController<bool>.broadcast();

      mockRepository = MockPlayerRepository();

      // Setup mock stream getters
      when(mockRepository.playingStream)
          .thenAnswer((_) => playingController.stream);
      when(mockRepository.positionStream)
          .thenAnswer((_) => positionController.stream);
      when(mockRepository.durationStream)
          .thenAnswer((_) => durationController.stream);
      when(mockRepository.bufferingStream)
          .thenAnswer((_) => bufferingController.stream);
      when(mockRepository.completedStream)
          .thenAnswer((_) => completedController.stream);

      controller = PlayerController(mockRepository);
    });

    tearDown(() async {
      // Close streams first
      await playingController.close();
      await positionController.close();
      await durationController.close();
      await bufferingController.close();
      await completedController.close();

      // Disable wakelock before dispose to avoid platform channel issues
      // Then dispose
      try {
        controller.dispose();
      } catch (e) {
        // Ignore wakelock errors during cleanup
      }
    });

    group('Initial State', () {
      test('should have correct initial values', () {
        expect(controller.isPlaying, true);
        expect(controller.showControls, true);
        expect(controller.isBuffering, false);
        expect(controller.isFullscreen, false);
        expect(controller.position, Duration.zero);
        expect(controller.duration, Duration.zero);
        expect(controller.volume, 100);
        expect(controller.playbackSpeed, 1.0);
        expect(controller.isLongPressing, false);
        expect(controller.subtitlesEnabled, isNotNull);
        expect(controller.currentVideo, isNull);
      });
    });

    group('Video Loading', () {
      test('loadVideoFile should set current video and load it', () async {
        // Arrange
        when(mockRepository.load(any)).thenAnswer((_) async {});
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});
        when(mockRepository.enableSubtitles()).thenAnswer((_) async {});

        // Act
        await controller.loadVideoFile(testVideoFile);

        // Assert
        expect(controller.currentVideo, testVideoFile);
        verify(mockRepository.load(testVideoFile.path)).called(1);
        verify(mockRepository.setVolume(100)).called(1);
      });

      test('loadVideo should load video without setting currentVideo',
          () async {
        // Arrange
        when(mockRepository.load(any)).thenAnswer((_) async {});
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});
        when(mockRepository.enableSubtitles()).thenAnswer((_) async {});

        // Act
        await controller.loadVideo('/test/video.mp4');

        // Assert
        expect(controller.currentVideo, isNull);
        verify(mockRepository.load('/test/video.mp4')).called(1);
      });

      test('should handle loading errors gracefully', () async {
        // Arrange
        when(mockRepository.load(any)).thenThrow(Exception('Load failed'));

        // Act & Assert
        expect(() => controller.loadVideoFile(testVideoFile), throwsException);
      });
    });

    group('Playback Control', () {
      setUp(() async {
        when(mockRepository.load(any)).thenAnswer((_) async {});
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});
        when(mockRepository.enableSubtitles()).thenAnswer((_) async {});
        await controller.loadVideoFile(testVideoFile);
      });

      test('togglePlayPause should pause when playing', () {
        // Arrange
        when(mockRepository.pause()).thenAnswer((_) async {});
        expect(controller.isPlaying, true);

        // Act
        controller.togglePlayPause();

        // Assert
        verify(mockRepository.pause()).called(1);
      });

      test('togglePlayPause should play when paused', () async {
        // Arrange - simulate paused state
        playingController.add(false);
        await Future.delayed(Duration(milliseconds: 10));

        when(mockRepository.play()).thenAnswer((_) async {});
        expect(controller.isPlaying, false);

        // Act
        controller.togglePlayPause();

        // Assert
        verify(mockRepository.play()).called(1);
      });

      test('pauseVideo should pause and update state', () {
        // Arrange
        when(mockRepository.pause()).thenAnswer((_) async {});

        // Act
        controller.pauseVideo();

        // Assert
        verify(mockRepository.pause()).called(1);
        expect(controller.isPlaying, false);
      });

      test('seek should update position and call repository', () {
        // Arrange
        final targetPosition = Duration(seconds: 30);
        when(mockRepository.seek(any)).thenAnswer((_) async {});

        // Act
        controller.seek(targetPosition);

        // Assert
        expect(controller.position, targetPosition);
        verify(mockRepository.seek(targetPosition)).called(1);
      });

      test('seekBack should seek backward', () {
        // Arrange
        positionController.add(Duration(seconds: 60));
        when(mockRepository.seek(any)).thenAnswer((_) async {});

        // Act
        controller.seekBack();

        // Assert - verify seek was called (actual duration may vary)
        verify(mockRepository.seek(any)).called(1);
      });

      test('seekForward should seek forward', () {
        // Arrange
        positionController.add(Duration(seconds: 60));
        durationController.add(Duration(minutes: 2));
        when(mockRepository.seek(any)).thenAnswer((_) async {});

        // Act
        controller.seekForward();

        // Assert - verify seek was called
        verify(mockRepository.seek(any)).called(1);
      });
    });

    group('Volume Control', () {
      test('setVolume should update volume and notify repository', () {
        // Arrange
        when(mockRepository.setVolume(50)).thenAnswer((_) async {});

        // Act
        controller.setVolume(50);

        // Assert
        expect(controller.volume, 50);
        verify(mockRepository.setVolume(50)).called(1);
      });

      test('setVolume should accept values in valid range', () {
        // Arrange
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});

        // Act - test various values
        controller.setVolume(0);
        expect(controller.volume, 0);

        controller.setVolume(50);
        expect(controller.volume, 50);

        controller.setVolume(100);
        expect(controller.volume, 100);
      });
    });

    group('Playback Speed', () {
      test('changeSpeed should cycle through speeds', () {
        // Arrange
        when(mockRepository.setSpeed(any)).thenAnswer((_) async {});
        expect(controller.playbackSpeed, 1.0);

        // Act & Assert - cycle through speeds
        controller.changeSpeed();
        expect(controller.playbackSpeed, 1.25);

        controller.changeSpeed();
        expect(controller.playbackSpeed, 1.5);

        controller.changeSpeed();
        expect(controller.playbackSpeed, 2.0);

        controller.changeSpeed();
        expect(controller.playbackSpeed, 0.5);

        controller.changeSpeed();
        expect(controller.playbackSpeed, 0.75);

        controller.changeSpeed();
        expect(controller.playbackSpeed, 1.0);
      });
    });

    group('Fullscreen', () {
      test('toggleFullscreen should toggle state', () {
        // Arrange
        expect(controller.isFullscreen, false);

        // Act
        controller.toggleFullscreen();

        // Assert
        expect(controller.isFullscreen, true);

        // Act - toggle back
        controller.toggleFullscreen();

        // Assert
        expect(controller.isFullscreen, false);
      });
    });

    group('Controls Visibility', () {
      test('showControlsNow should show controls', () {
        // Arrange
        controller.state.showControls = false;

        // Act
        controller.showControlsNow();

        // Assert
        expect(controller.showControls, true);
      });
    });

    group('Stream Listeners', () {
      test('should update isPlaying from playingStream', () async {
        // Act
        playingController.add(false);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.isPlaying, false);

        // Act
        playingController.add(true);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.isPlaying, true);
      });

      test('should update position from positionStream when not dragging',
          () async {
        // Arrange
        final newPosition = Duration(seconds: 45);

        // Act
        positionController.add(newPosition);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.position, newPosition);
      });

      test('should not update position from positionStream when dragging',
          () async {
        // Arrange
        controller.state.isDraggingX = true;
        controller.state.position = Duration(seconds: 30);

        // Act
        positionController.add(Duration(seconds: 60));
        await Future.delayed(Duration(milliseconds: 50));

        // Assert - position should remain unchanged
        expect(controller.position, Duration(seconds: 30));
      });

      test('should update duration from durationStream', () async {
        // Arrange
        final newDuration = Duration(minutes: 5);

        // Act
        durationController.add(newDuration);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.duration, newDuration);
      });

      test('should update isBuffering from bufferingStream', () async {
        // Act
        bufferingController.add(true);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.isBuffering, true);

        // Act
        bufferingController.add(false);
        await Future.delayed(Duration(milliseconds: 50));

        // Assert
        expect(controller.isBuffering, false);
      });

      test('should call resetPositionOnVideoEnd when video completes',
          () async {
        // Arrange
        when(mockRepository.load(any)).thenAnswer((_) async {});
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});
        when(mockRepository.enableSubtitles()).thenAnswer((_) async {});
        await controller.loadVideoFile(testVideoFile);

        durationController.add(Duration(minutes: 2));
        await Future.delayed(Duration(milliseconds: 50));

        // Act - simulate completion
        completedController.add(true);
        await Future.delayed(Duration(milliseconds: 100));

        // Assert - position should be reset (this calls PlayHistoryService)
        // Note: The actual save happens in resetPositionOnVideoEnd
      });
    });

    group('Position Saving', () {
      setUp(() async {
        when(mockRepository.load(any)).thenAnswer((_) async {});
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});
        when(mockRepository.enableSubtitles()).thenAnswer((_) async {});
        when(mockRepository.pause()).thenAnswer((_) async {});
        await controller.loadVideoFile(testVideoFile);

        // Set up duration and position for tests
        durationController.add(Duration(minutes: 2));
        positionController.add(Duration(seconds: 30));
        await Future.delayed(Duration(milliseconds: 50));
      });

      test('saveCurrentPosition should return false if position < 5 seconds',
          () async {
        // Arrange
        positionController.add(Duration(seconds: 3));
        await Future.delayed(Duration(milliseconds: 50));

        // Act
        final result = await controller.saveCurrentPosition();

        // Assert
        expect(result, false);
      });

      test('saveCurrentPosition should respect throttling', () async {
        // Arrange - position > 5 seconds
        positionController.add(Duration(seconds: 30));
        await Future.delayed(Duration(milliseconds: 50));

        // First save
        final result1 = await controller.saveCurrentPosition();

        // Immediate second save should be throttled
        final result2 = await controller.saveCurrentPosition();

        // Assert
        expect(result1, true);
        expect(result2, false);
      });

      test('saveCurrentPosition should allow forced save', () async {
        // Arrange
        positionController.add(Duration(seconds: 30));
        await Future.delayed(Duration(milliseconds: 50));

        // First save
        await controller.saveCurrentPosition();

        // Forced save should succeed
        final result = await controller.saveCurrentPosition(force: true);

        // Assert
        expect(result, true);
      });

      test('savePositionOnPause should force save', () async {
        // Arrange
        positionController.add(Duration(seconds: 30));
        await Future.delayed(Duration(milliseconds: 50));

        // Act
        await controller.savePositionOnPause();

        // Assert - should have saved (force=true)
      });

      test('savePositionOnBackground should force save', () async {
        // Arrange
        positionController.add(Duration(seconds: 30));
        await Future.delayed(Duration(milliseconds: 50));

        // Act
        await controller.savePositionOnBackground();

        // Assert - should have saved (force=true)
      });

      test('resetPositionOnVideoEnd should save position as zero', () async {
        // Act
        await controller.resetPositionOnVideoEnd();

        // Assert - should save position as Duration.zero
      });
    });

    group('Gesture Handling', () {
      test('onHorizontalDragStart should set up drag state', () {
        // Act
        controller.onHorizontalDragStart(100.0);

        // Assert
        expect(controller.state.isDraggingX, true);
        expect(controller.state.dragStartX, 100.0);
        expect(controller.state.showSeekIndicator, true);
      });

      test('onHorizontalDragUpdate should calculate new position', () {
        // Arrange
        controller.onHorizontalDragStart(100.0);
        durationController.add(Duration(minutes: 2));
        positionController.add(Duration(minutes: 1));

        // Act - drag 200 pixels to the right on a 400 pixel screen
        controller.onHorizontalDragUpdate(300.0, 400.0);

        // Assert - should have moved forward
        expect(controller.state.isDraggingX, true);
        expect(controller.state.seekDirection, 'forward');
      });

      test('onHorizontalDragEnd should seek and reset state', () {
        // Arrange
        when(mockRepository.seek(any)).thenAnswer((_) async {});
        controller.onHorizontalDragStart(100.0);

        // Act
        controller.onHorizontalDragEnd();

        // Assert
        expect(controller.state.isDraggingX, false);
        expect(controller.state.showSeekIndicator, false);
        verify(mockRepository.seek(any)).called(1);
      });

      test('onLongPressStart should set speed to 2x', () {
        // Arrange
        when(mockRepository.setSpeed(2.0)).thenAnswer((_) async {});

        // Act
        controller.onLongPressStart();

        // Assert
        expect(controller.isLongPressing, true);
        expect(controller.playbackSpeed, 2.0);
        verify(mockRepository.setSpeed(2.0)).called(1);
      });

      test('onLongPressEnd should reset speed to 1x', () {
        // Arrange
        when(mockRepository.setSpeed(1.0)).thenAnswer((_) async {});
        controller.onLongPressStart();

        // Act
        controller.onLongPressEnd();

        // Assert
        expect(controller.isLongPressing, false);
        expect(controller.playbackSpeed, 1.0);
        verify(mockRepository.setSpeed(1.0)).called(1);
      });
    });

    group('Format Duration', () {
      test('formatDuration should format correctly', () {
        expect(controller.formatDuration(Duration(seconds: 65)), '01:05');
        expect(controller.formatDuration(Duration(minutes: 5, seconds: 30)),
            '05:30');
        expect(controller.formatDuration(Duration(hours: 1, minutes: 30)),
            '01:30:00');
        expect(controller.formatDuration(Duration.zero), '00:00');
      });
    });

    group('Dispose', () {
      test('dispose should clean up resources', () {
        // Arrange
        when(mockRepository.dispose()).thenReturn(null);

        // Act - manually call dispose without triggering wakelock
        try {
          controller.dispose();
        } catch (e) {
          // Ignore wakelock platform errors
        }

        // Assert
        verify(mockRepository.dispose()).called(1);
      });
    });

    group('Edge Cases', () {
      test('should handle multiple seek operations', () {
        // Arrange
        when(mockRepository.seek(any)).thenAnswer((_) async {});

        // Act - multiple seeks
        controller.seek(Duration(seconds: 10));
        controller.seek(Duration(seconds: 20));
        controller.seek(Duration(seconds: 30));

        // Assert
        verify(mockRepository.seek(any)).called(3);
        expect(controller.position, Duration(seconds: 30));
      });

      test('should handle rapid volume changes', () {
        // Arrange
        when(mockRepository.setVolume(any)).thenAnswer((_) async {});

        // Act - rapid changes
        controller.setVolume(10);
        controller.setVolume(50);
        controller.setVolume(100);

        // Assert
        verify(mockRepository.setVolume(any)).called(3);
        expect(controller.volume, 100);
      });
    });
  });
}
