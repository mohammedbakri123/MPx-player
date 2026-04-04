import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import '../../../reels/presentation/widgets/playback_speed_indicator.dart';
import '../../../reels/presentation/widgets/seek_feedback_indicator.dart';

class OverlayLayer extends StatelessWidget {
  final PlayerController controller;

  const OverlayLayer({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 200 || constraints.maxHeight < 120) {
          return const SizedBox.shrink();
        }
        return Stack(
          fit: StackFit.expand,
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
      },
    );
  }

  Widget _buildDoubleTapSeekLeft() {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekLeft
          ? const Positioned(
              key: ValueKey<bool>(true),
              left: 24,
              top: 0,
              bottom: 80,
              child: Center(
                child: SeekFeedbackIndicator(isForward: false),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildDoubleTapSeekRight() {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekRight
          ? const Positioned(
              key: ValueKey<bool>(true),
              right: 24,
              top: 0,
              bottom: 80,
              child: Center(
                child: SeekFeedbackIndicator(isForward: true),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildSpeedIndicator() {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.isLongPressing
          ? const Positioned(
              key: ValueKey<bool>(true),
              top: 120,
              left: 0,
              right: 0,
              child: Center(
                child: PlaybackSpeedIndicator(speed: 2.0),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildVolumeIndicator(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.showVolumeIndicator
          ? Align(
              key: const ValueKey<bool>(true),
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(left: 30),
                child: _buildVerticalIndicator(
                  icon: controller.volume == 0
                      ? Icons.volume_off
                      : Icons.volume_up,
                  value: controller.volume / 100,
                  color: Colors.blue,
                  label: '${controller.volume.toInt()}',
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildBrightnessIndicator(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.showBrightnessIndicator
          ? Align(
              key: const ValueKey<bool>(true),
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.only(right: 30),
                child: _buildVerticalIndicator(
                  icon: Icons.brightness_6,
                  value: controller.brightnessValue / 100,
                  color: Colors.yellow,
                  label: '${controller.brightnessValue.toInt()}',
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildSeekIndicator() {
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
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.55),
                  borderRadius: BorderRadius.circular(24),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      controller.seekDirection == 'forward'
                          ? Icons.forward_10_rounded
                          : Icons.replay_10_rounded,
                      color: Colors.white,
                      size: 28,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      controller.formatDuration(controller.position),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
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
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 200),
      transitionBuilder: (child, animation) {
        return FadeTransition(opacity: animation, child: child);
      },
      child: controller.isBuffering
          ? Center(
              key: const ValueKey<bool>(true),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.5),
                  shape: BoxShape.circle,
                ),
                child: const CircularProgressIndicator(
                  color: Colors.white,
                  strokeWidth: 3,
                ),
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildLockedIndicator() {
    return const AnimatedSwitcher(
      duration: Duration(milliseconds: 200),
      child: SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildVerticalIndicator({
    required IconData icon,
    required double value,
    required Color color,
    required String label,
  }) {
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
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    return Container(
                      width: 4,
                      height: constraints.maxHeight * value,
                      decoration: BoxDecoration(
                        color: color,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    );
                  },
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
