import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';

/// OverlayLayer
///
/// This file contains the `OverlayLayer` widget used by the video player UI.
///
/// Responsibilities:
/// - Render transient overlay elements above the video (seek animations, buffering
///   spinner, volume/brightness indicators, speed/long-press indicator, locked
///   state UI, etc.).
/// - Use small, self-contained private builder methods to show/hide each
///   overlay element based on state exposed by `PlayerController`.
/// - Keep the overlay presentation separate from playback logic. This widget
///   observes properties on `PlayerController` (passed in) and renders
///   animations and indicators accordingly.
///
/// Notes:
/// - No playback logic or file I/O is performed here. This widget is purely
///   presentational and receives state from the controller.

class OverlayLayer extends StatelessWidget {
  final PlayerController controller;

  const OverlayLayer({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        _buildDoubleTapSeekLeft(),
        _buildDoubleTapSeekRight(),
        _buildSpeedIndicator(),
        _buildVolumeIndicator(context),
        _buildBrightnessIndicator(context),
        _buildSeekIndicator(),
        _buildBufferingIndicator(),
        _buildLockedIndicator(),
      ],
    );
  }

  Widget _buildDoubleTapSeekLeft() {
    // Double-tap left seek animation
    // Shows a short animated icon on the left side when the controller
    // indicates a double-tap-seek-left event occurred. The visibility is
    // controlled by `controller.showDoubleTapSeekLeft` and the widget uses an
    // AnimatedSwitcher for a smooth appearance/disappearance.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekLeft
          ? const Positioned(
              key: ValueKey<bool>(true),
              left: 40,
              top: 0,
              bottom: 0,
              child: _DoubleTapSeekAnimation(
                icon: Icons.fast_rewind,
                direction: SeekAnimationDirection.left,
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildDoubleTapSeekRight() {
    // Double-tap right seek animation
    // Mirrors the left-side animation but appears on the right when the
    // controller signals a double-tap-seek-right event.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekRight
          ? const Positioned(
              key: ValueKey<bool>(true),
              right: 40,
              top: 0,
              bottom: 0,
              child: _DoubleTapSeekAnimation(
                icon: Icons.fast_forward,
                direction: SeekAnimationDirection.right,
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildSpeedIndicator() {
    // Long-press playback speed indicator
    // When the user long-presses to fast-scrub or change speed, show a center
    // overlay indicating the current speed. This example shows '2.0x'. The
    // controller exposes `isLongPressing` which toggles this indicator.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.isLongPressing
          ? Center(
              key: const ValueKey<bool>(true),
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Text(
                  '2.0x',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildVolumeIndicator(BuildContext context) {
    // Volume indicator shown vertically on the right side of the screen.
    // The controller exposes `showVolumeIndicator` and a `volume` value
    // (expected 0..100). We render a small vertical bar and value label.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.showVolumeIndicator
          ? Positioned(
              key: const ValueKey<bool>(true),
              right: 30,
              top: MediaQuery.of(context).size.height / 2 - 60,
              child: _buildVerticalIndicator(
                icon:
                    controller.volume == 0 ? Icons.volume_off : Icons.volume_up,
                value: controller.volume / 100,
                color: Colors.blue,
                label: '${controller.volume.toInt()}',
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildBrightnessIndicator(BuildContext context) {
    // Brightness indicator shown vertically on the left side of the screen.
    // Uses controller.showBrightnessIndicator and controller.brightnessValue
    // (expected normalized 0.0..1.0).
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.showBrightnessIndicator
          ? Positioned(
              key: const ValueKey<bool>(true),
              left: 30,
              top: MediaQuery.of(context).size.height / 2 - 60,
              child: _buildVerticalIndicator(
                icon: Icons.brightness_6,
                value: controller.brightnessValue,
                color: Colors.yellow,
                label: '${(controller.brightnessValue * 100).toInt()}',
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildSeekIndicator() {
    // Transient seek indicator shown at center when the user is performing a
    // seek gesture. Displays an icon (forward/back) and the formatted
    // position/time returned by controller.formatDuration(controller.position).
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.showSeekIndicator
          ? Center(
              key: const ValueKey<bool>(true),
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      controller.seekDirection == 'forward'
                          ? Icons.forward_10
                          : Icons.replay_10,
                      color: Colors.white,
                      size: 24,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      controller.formatDuration(controller.position),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildBufferingIndicator() {
    // Shows a centered CircularProgressIndicator when `controller.isBuffering`
    // is true. Uses AnimatedSwitcher for smooth fade.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.isBuffering
          ? const Center(
              key: ValueKey<bool>(true),
              child: CircularProgressIndicator(
                color: Colors.white,
                strokeWidth: 3,
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildLockedIndicator() {
    // When the player UI is locked (e.g. to prevent accidental touches),
    // show a subtle 'locked' overlay. The controller toggles this via
    // `controller.isLocked`.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      child: controller.isLocked
          ? Center(
              key: const ValueKey<bool>(true),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.7),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.lock,
                      color: Colors.white70,
                      size: 48,
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Tap to unlock',
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildVerticalIndicator({
    required IconData icon,
    required double value,
    required Color color,
    required String label,
  }) {
    // Generic vertical indicator used for both volume and brightness UI.
    // - `icon` shows the glyph (volume or brightness)
    // - `value` expected to be 0.0..1.0 and controls the filled height
    // - `label` displays a small text label (percentage or numeric value)
    return Container(
      width: 50,
      height: 120,
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(25),
      ),
      child: Column(
        children: [
          Icon(icon, color: Colors.white, size: 20),
          const SizedBox(height: 8),
          Expanded(
            child: Container(
              width: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade700,
                borderRadius: BorderRadius.circular(2),
              ),
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Container(
                  width: 4,
                  height: 100 * value,
                  decoration: BoxDecoration(
                    color: color,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(label,
              style: const TextStyle(color: Colors.white, fontSize: 10)),
        ],
      ),
    );
  }
}

enum SeekAnimationDirection { left, right }

class _DoubleTapSeekAnimation extends StatefulWidget {
  final IconData icon;
  final SeekAnimationDirection direction;

  const _DoubleTapSeekAnimation({
    required this.icon,
    required this.direction,
  });

  /// Small animated circular icon used to visualize double-tap seek events.
  /// When created it plays a short scale+fade animation and then naturally
  /// fades away. This widget is intentionally lightweight and self-contained.

  @override
  State<_DoubleTapSeekAnimation> createState() =>
      _DoubleTapSeekAnimationState();
}

class _DoubleTapSeekAnimationState extends State<_DoubleTapSeekAnimation>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );

    _scaleAnimation = Tween<double>(begin: 0.5, end: 1.2).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );

    _fadeAnimation = Tween<double>(begin: 1.0, end: 0.0).animate(
      CurvedAnimation(parent: _controller, curve: const Interval(0.5, 1.0)),
    );

    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Opacity(
          opacity: _fadeAnimation.value,
          child: Transform.scale(
            scale: _scaleAnimation.value,
            child: Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.5),
                shape: BoxShape.circle,
              ),
              child: Icon(
                widget.icon,
                color: Colors.white,
                size: 40,
              ),
            ),
          ),
        );
      },
    );
  }
}
