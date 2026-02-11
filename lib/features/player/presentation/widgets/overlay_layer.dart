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
        // Speed indicator
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 200),
          transitionBuilder: (child, animation) {
            return FadeTransition(opacity: animation, child: child);
          },
          child: controller.isLongPressing
              ? Center(
                  key: const ValueKey<bool>(true),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
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
        ),

        // Volume indicator
        AnimatedSwitcher(
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
                    icon: controller.volume == 0 ? Icons.volume_off : Icons.volume_up,
                    value: controller.volume / 100,
                    color: Colors.blue,
                    label: '${controller.volume.toInt()}',
                  ),
                )
              : const SizedBox.shrink(key: ValueKey<bool>(false)),
        ),

        // Brightness indicator
        AnimatedSwitcher(
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
        ),

        // Seek indicator
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 200),
          transitionBuilder: (child, animation) {
            return FadeTransition(opacity: animation, child: child);
          },
          child: controller.showSeekIndicator
              ? Center(
                  key: const ValueKey<bool>(true),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
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
                          controller.formatTime(controller.position),
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
        ),

        // Buffering indicator
        AnimatedSwitcher(
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
        ),
      ],
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
