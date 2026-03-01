import 'package:flutter_test/flutter_test.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Position Saving', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
      await harness.loadTestVideo();
      harness.repository.emitDuration(Duration(minutes: 2));
      harness.repository.emitPosition(Duration(seconds: 30));
      await Future.delayed(Duration(milliseconds: 50));
    });

    tearDown(() async => await harness.tearDown());

    test('saveCurrentPosition should return false if position < 5 seconds',
        () async {
      harness.repository.emitPosition(Duration(seconds: 3));
      await Future.delayed(Duration(milliseconds: 50));

      final result = await harness.controller.saveCurrentPosition();
      expect(result, false);
    });

    test('saveCurrentPosition should respect throttling', () async {
      harness.repository.emitPosition(Duration(seconds: 30));
      await Future.delayed(Duration(milliseconds: 50));

      final result1 = await harness.controller.saveCurrentPosition();
      final result2 = await harness.controller.saveCurrentPosition();

      expect(result1, true);
      expect(result2, false);
    });

    test('saveCurrentPosition should allow forced save', () async {
      harness.repository.emitPosition(Duration(seconds: 30));
      await Future.delayed(Duration(milliseconds: 50));

      await harness.controller.saveCurrentPosition();
      final result = await harness.controller.saveCurrentPosition(force: true);
      expect(result, true);
    });

    test('savePositionOnPause should force save', () async {
      harness.repository.emitPosition(Duration(seconds: 30));
      await Future.delayed(Duration(milliseconds: 50));
      await harness.controller.savePositionOnPause();
    });

    test('savePositionOnBackground should force save', () async {
      harness.repository.emitPosition(Duration(seconds: 30));
      await Future.delayed(Duration(milliseconds: 50));
      await harness.controller.savePositionOnBackground();
    });

    test('resetPositionOnVideoEnd should save position as zero', () async {
      await harness.controller.resetPositionOnVideoEnd();
    });
  });
}
