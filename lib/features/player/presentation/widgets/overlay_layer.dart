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
        return IgnorePointer(
          child: Stack(
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
          ),
        );
      },
    );
  }

  Widget _buildDoubleTapSeekLeft() {
    if (!controller.showDoubleTapSeekLeft) {
      return const SizedBox.shrink();
    }
    return Positioned(
      left: 24,
      top: 0,
      bottom: 80,
      child: Center(
        child: SeekFeedbackIndicator(
          isForward: false,
          forwardedTime: controller.doubleTapSeekStep,
        ),
      ),
    );
  }

  Widget _buildDoubleTapSeekRight() {
    if (!controller.showDoubleTapSeekRight) {
      return const SizedBox.shrink();
    }
    return Positioned(
      right: 24,
      top: 0,
      bottom: 80,
      child: Center(
        child: SeekFeedbackIndicator(
          isForward: true,
          forwardedTime: controller.doubleTapSeekStep,
        ),
      ),
    );
  }

  Widget _buildSpeedIndicator() {
    if (!controller.isLongPressing) {
      return const SizedBox.shrink();
    }

    return const Positioned(
      top: 120,
      left: 0,
      right: 0,
      child: Center(
        child: PlaybackSpeedIndicator(speed: 2.0),
      ),
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
    final progress = controller.duration.inMilliseconds > 0
        ? controller.position.inMilliseconds /
            controller.duration.inMilliseconds
        : 0.0;

    if (!controller.showSeekIndicator) {
      return const SizedBox.shrink();
    }

    return Positioned(
      bottom: 100,
      left: 0,
      right: 0,
      child: Center(
        child: Container(
          width: 160,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.7),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                controller.formatDuration(controller.position),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(2),
                child: LinearProgressIndicator(
                  value: progress.clamp(0.0, 1.0),
                  minHeight: 3,
                  backgroundColor: Colors.white24,
                  valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                controller.formatDuration(controller.duration),
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.5),
                  fontSize: 11,
                ),
              ),
            ],
          ),
        ),
      ),
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
