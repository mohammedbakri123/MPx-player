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
        if (controller.isLongPressing)
          Center(
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
          ),

        // Volume indicator
        if (controller.showVolumeIndicator)
          Positioned(
            right: 30,
            top: MediaQuery.of(context).size.height / 2 - 60,
            child: _buildVerticalIndicator(
              icon: controller.volume == 0 ? Icons.volume_off : Icons.volume_up,
              value: controller.volume / 100,
              color: Colors.blue,
              label: '${controller.volume.toInt()}',
            ),
          ),

        // Brightness indicator
        if (controller.showBrightnessIndicator)
          Positioned(
            left: 30,
            top: MediaQuery.of(context).size.height / 2 - 60,
            child: _buildVerticalIndicator(
              icon: Icons.brightness_6,
              value: controller.brightnessValue,
              color: Colors.yellow,
              label: '${(controller.brightnessValue * 100).toInt()}',
            ),
          ),

        // Seek indicator
        if (controller.showSeekIndicator)
          Center(
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
          ),

        // Buffering indicator
        if (controller.isBuffering)
          const Center(
            child: CircularProgressIndicator(
              color: Colors.white,
              strokeWidth: 3,
            ),
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
