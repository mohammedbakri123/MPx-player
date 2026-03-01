import 'package:flutter_test/flutter_test.dart';
import 'player_controller_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  PlayerControllerTestHarness.setupPlatformChannels();

  group('PlayerController - Misc', () {
    late PlayerControllerTestHarness harness;

    setUp(() async {
      harness = PlayerControllerTestHarness();
      await harness.setUp();
    });

    tearDown(() async => await harness.tearDown());

    group('Fullscreen & Controls', () {
      test('toggleFullscreen should toggle state', () {
        expect(harness.controller.isFullscreen, false);

        harness.controller.toggleFullscreen();
        expect(harness.controller.isFullscreen, true);

        harness.controller.toggleFullscreen();
        expect(harness.controller.isFullscreen, false);
      });

      test('showControlsNow should show controls', () {
        harness.controller.state.showControls = false;
        harness.controller.showControlsNow();
        expect(harness.controller.showControls, true);
      });
    });

    group('Format Duration', () {
      test('formatDuration should format correctly', () {
        expect(harness.controller.formatDuration(Duration(seconds: 65)), '01:05');
        expect(
          harness.controller.formatDuration(Duration(minutes: 5, seconds: 30)),
          '05:30',
        );
        expect(
          harness.controller.formatDuration(Duration(hours: 1, minutes: 30)),
          '01:30:00',
        );
        expect(harness.controller.formatDuration(Duration.zero), '00:00');
      });
    });

    group('Dispose', () {
      test('dispose should clean up resources', () {
        harness.controller.dispose();
      });
    });

    group('Edge Cases', () {
      test('should handle multiple seek operations', () {
        harness.controller.seek(Duration(seconds: 10));
        harness.controller.seek(Duration(seconds: 20));
        harness.controller.seek(Duration(seconds: 30));

        expect(harness.controller.position, Duration(seconds: 30));
      });

      test('should handle rapid volume changes', () {
        harness.controller.setVolume(10);
        harness.controller.setVolume(50);
        harness.controller.setVolume(100);

        expect(harness.controller.volume, 100);
      });
    });
  });
}
