import 'package:flutter_test/flutter_test.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Gesture Handling', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
    });

    tearDown(() async => await harness.tearDown());

    test('onHorizontalDragStart should set up drag state', () {
      harness.controller.onHorizontalDragStart(100.0);

      expect(harness.controller.state.isDraggingX, true);
      expect(harness.controller.state.dragStartX, 100.0);
      expect(harness.controller.state.showSeekIndicator, true);
    });

    test('onHorizontalDragUpdate should calculate new position', () {
      harness.controller.onHorizontalDragStart(100.0);
      harness.repository.emitDuration(Duration(minutes: 2));
      harness.repository.emitPosition(Duration(minutes: 1));

      harness.controller.onHorizontalDragUpdate(300.0, 400.0);

      expect(harness.controller.state.isDraggingX, true);
      expect(harness.controller.state.seekDirection, 'forward');
    });

    test('onHorizontalDragEnd should seek and reset state', () async {
      harness.controller.onHorizontalDragStart(100.0);
      harness.controller.onHorizontalDragEnd();

      expect(harness.controller.state.isDraggingX, false);
      expect(harness.controller.state.showSeekIndicator, false);
    });

    test('onLongPressStart should set speed to 2x', () {
      harness.controller.onLongPressStart();

      expect(harness.controller.isLongPressing, true);
      expect(harness.controller.playbackSpeed, 2.0);
    });

    test('onLongPressEnd should reset speed to 1x', () {
      harness.controller.onLongPressStart();
      harness.controller.onLongPressEnd();

      expect(harness.controller.isLongPressing, false);
      expect(harness.controller.playbackSpeed, 1.0);
    });
  });
}
