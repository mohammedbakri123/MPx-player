import 'package:flutter_test/flutter_test.dart';
import 'package:mpx/features/player/domain/repositories/player_repository.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Initial State & Loading', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
    });

    tearDown(() async => await harness.tearDown());

    group('Initial State', () {
      test('should have correct initial values', () {
        expect(harness.controller.isPlaying, true);
        expect(harness.controller.showControls, true);
        expect(harness.controller.isBuffering, false);
        expect(harness.controller.isFullscreen, false);
        expect(harness.controller.position, Duration.zero);
        expect(harness.controller.duration, Duration.zero);
        expect(harness.controller.volume, 100);
        expect(harness.controller.playbackSpeed, 1.0);
        expect(harness.controller.isLongPressing, false);
        expect(harness.controller.subtitlesEnabled, isNotNull);
        expect(harness.controller.currentVideo, isNull);
      });
    });

    group('Video Loading', () {
      test('loadVideoFile should set current video and load it', () async {
        await harness.controller.loadVideoFile(harness.testVideoFile);

        expect(harness.controller.currentVideo, harness.testVideoFile);
      });

      test('loadVideo should load video without setting currentVideo', () async {
        await harness.controller.loadVideo('/test/video.mp4');

        expect(harness.controller.currentVideo, isNull);
      });
    });
  });
}
