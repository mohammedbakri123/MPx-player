import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';

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
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekLeft
          ? Positioned(
              key: const ValueKey<bool>(true),
              left: 40,
              top: 0,
              bottom: 0,
              child: const _DoubleTapSeekAnimation(
                icon: Icons.fast_rewind,
                direction: SeekAnimationDirection.left,
              ),
            )
          : const SizedBox.shrink(key: ValueKey<bool>(false)),
    );
  }

  Widget _buildDoubleTapSeekRight() {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      child: controller.showDoubleTapSeekRight
          ? Positioned(
              key: const ValueKey<bool>(true),
              right: 40,
              top: 0,
              bottom: 0,
              child: const _DoubleTapSeekAnimation(
                icon: Icons.fast_forward,
                direction: SeekAnimationDirection.right,
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
