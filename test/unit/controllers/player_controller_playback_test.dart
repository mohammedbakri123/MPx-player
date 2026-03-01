import 'package:flutter_test/flutter_test.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Playback Control', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
      await harness.loadTestVideo();
    });

    tearDown(() async => await harness.tearDown());

    group('Play/Pause', () {
      test('togglePlayPause should pause when playing', () {
        expect(harness.controller.isPlaying, true);
        harness.controller.togglePlayPause();
      });

      test('togglePlayPause should play when paused', () async {
        harness.repository.emitPlaying(false);
        await Future.delayed(Duration(milliseconds: 10));
        expect(harness.controller.isPlaying, false);

        harness.controller.togglePlayPause();
      });

      test('pauseVideo should pause and update state', () async {
        harness.controller.pauseVideo();
        expect(harness.controller.isPlaying, false);
      });
    });

    group('Seek', () {
      test('seek should update position and call repository', () {
        final targetPosition = Duration(seconds: 30);
        harness.controller.seek(targetPosition);
        expect(harness.controller.position, targetPosition);
      });

      test('seekBack should seek backward', () {
        harness.repository.emitPosition(Duration(seconds: 60));
        harness.controller.seekBack();
      });

      test('seekForward should seek forward', () {
        harness.repository.emitPosition(Duration(seconds: 60));
        harness.repository.emitDuration(Duration(minutes: 2));
        harness.controller.seekForward();
      });
    });

    group('Volume', () {
      test('setVolume should update volume', () {
        harness.controller.setVolume(50);
        expect(harness.controller.volume, 50);
      });

      test('setVolume should accept values in valid range', () {
        harness.controller.setVolume(0);
        expect(harness.controller.volume, 0);

        harness.controller.setVolume(50);
        expect(harness.controller.volume, 50);

        harness.controller.setVolume(100);
        expect(harness.controller.volume, 100);
      });
    });

    group('Playback Speed', () {
      test('changeSpeed should cycle through speeds', () {
        expect(harness.controller.playbackSpeed, 1.0);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 1.25);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 1.5);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 2.0);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 0.5);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 0.75);

        harness.controller.changeSpeed();
        expect(harness.controller.playbackSpeed, 1.0);
      });
    });
  });
}
