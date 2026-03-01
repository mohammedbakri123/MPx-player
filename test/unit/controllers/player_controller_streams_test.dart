import 'package:flutter_test/flutter_test.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Stream Listeners', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
    });

    tearDown(() async => await harness.tearDown());

    test('should update isPlaying from playingStream', () async {
      harness.repository.emitPlaying(false);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.isPlaying, false);

      harness.repository.emitPlaying(true);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.isPlaying, true);
    });

    test('should update position from positionStream when not dragging',
        () async {
      final newPosition = Duration(seconds: 45);
      harness.repository.emitPosition(newPosition);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.position, newPosition);
    });

    test('should not update position from positionStream when dragging',
        () async {
      harness.controller.state.isDraggingX = true;
      harness.controller.state.position = Duration(seconds: 30);

      harness.repository.emitPosition(Duration(seconds: 60));
      await Future.delayed(Duration(milliseconds: 50));

      expect(harness.controller.position, Duration(seconds: 30));
    });

    test('should update duration from durationStream', () async {
      final newDuration = Duration(minutes: 5);
      harness.repository.emitDuration(newDuration);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.duration, newDuration);
    });

    test('should update isBuffering from bufferingStream', () async {
      harness.repository.emitBuffering(true);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.isBuffering, true);

      harness.repository.emitBuffering(false);
      await Future.delayed(Duration(milliseconds: 50));
      expect(harness.controller.isBuffering, false);
    });

    test('should call resetPositionOnVideoEnd when video completes', () async {
      await harness.loadTestVideo();
      harness.repository.emitDuration(Duration(minutes: 2));
      await Future.delayed(Duration(milliseconds: 50));

      harness.repository.emitCompleted(true);
      await Future.delayed(Duration(milliseconds: 100));
    });
  });
}
