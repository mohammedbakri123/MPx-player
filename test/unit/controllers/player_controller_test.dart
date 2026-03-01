import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mpx/features/player/controller/player_controller.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/player/services/play_history_service.dart';
import 'package:mpx/features/settings/services/subtitle_settings_service.dart';
import '../helpers/test_player_repository.dart';

/// Test harness for PlayerController tests.
/// Provides common setup, teardown, and test fixtures.
class PlayerControllerTestHarness {
  late PlayerController controller;
  late TestPlayerRepository repository;

  final testVideoFile = VideoFile(
    id: 'test-video-1',
    path: '/test/videos/test.mp4',
    title: 'test.mp4',
    folderPath: '/test/videos',
    folderName: 'videos',
    size: 1024 * 1024 * 500,
    duration: 120000,
    dateAdded: DateTime.now(),
    width: 1920,
    height: 1080,
  );

  static void setupPlatformChannels() {
    const MethodChannel wakelockChannel =
        MethodChannel('dev.fluttercommunity.plus/wakelock');
    const MethodChannel pigeonChannel =
        MethodChannel('dev.flutter.pigeon.wakelock_plus_platform_interface.WakelockPlusApi.toggle');

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(wakelockChannel, (MethodCall call) async => true);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(pigeonChannel, (MethodCall call) async => true);
  }

  Future<void> setUp() async {
    SharedPreferences.setMockInitialValues({});
    await PlayHistoryService.init();
    await SubtitleSettingsService.init();

    repository = TestPlayerRepository();
    controller = PlayerController(repository);
  }

  Future<void> tearDown() async {
    // Give time for any pending operations
    await Future.delayed(Duration(milliseconds: 10));
    
    try {
      controller.dispose();
    } catch (e) {
      // Ignore wakelock errors during cleanup
    }
    
    // Give time for streams to drain
    await Future.delayed(Duration(milliseconds: 50));
  }

  Future<void> loadTestVideo() async {
    await controller.loadVideoFile(testVideoFile);
  }
}
