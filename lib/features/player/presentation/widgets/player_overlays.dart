import 'package:flutter/material.dart';

class PlayerOverlays extends StatelessWidget {
  final bool isLongPressing;
  final bool showVolumeIndicator;
  final bool showBrightnessIndicator;
  final bool showSeekIndicator;
  final double volume;
  final double brightnessValue;
  final String seekDirection;
  final Duration position;
  final Duration duration;
  final String Function(Duration) formatTime;

  const PlayerOverlays({
    super.key,
    required this.isLongPressing,
    required this.showVolumeIndicator,
    required this.showBrightnessIndicator,
    required this.showSeekIndicator,
    required this.volume,
    required this.brightnessValue,
    required this.seekDirection,
    required this.position,
    required this.duration,
    required this.formatTime,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        if (isLongPressing)
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
        if (showVolumeIndicator)
          Positioned(
            right: 30,
            top: MediaQuery.of(context).size.height / 2 - 60,
            child: _buildVerticalIndicator(
              icon: volume == 0 ? Icons.volume_off : Icons.volume_up,
              value: volume / 100,
              color: Colors.blue,
              label: '${volume.toInt()}',
            ),
          ),
        if (showBrightnessIndicator)
          Positioned(
            left: 30,
            top: MediaQuery.of(context).size.height / 2 - 60,
            child: _buildVerticalIndicator(
              icon: Icons.brightness_6,
              value: brightnessValue,
              color: Colors.yellow,
              label: '${(brightnessValue * 100).toInt()}',
            ),
          ),
        if (showSeekIndicator)
          Center(
            child: _buildSeekIndicator(context),
          ),
      ],
    );
  }

  Widget _buildSeekIndicator(BuildContext context) {
    final progress = duration.inMilliseconds > 0
        ? position.inMilliseconds / duration.inMilliseconds
        : 0.0;

    return Container(
      width: 200,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.8),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            seekDirection == 'forward'
                ? Icons.fast_forward_rounded
                : Icons.fast_rewind_rounded,
            color: Colors.white,
            size: 28,
          ),
          const SizedBox(height: 8),
          Text(
            formatTime(position),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '${formatTime(position)} / ${formatTime(duration)}',
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.6),
              fontSize: 11,
            ),
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: progress.clamp(0.0, 1.0),
              minHeight: 4,
              backgroundColor: Colors.white24,
              valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
            ),
          ),
        ],
      ),
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
