import 'package:flutter/material.dart';

class BottomControls extends StatelessWidget {
  final Duration position;
  final Duration duration;
  final double playbackSpeed;
  final bool isPlaying;
  final String Function(Duration) formatTime;
  final ValueChanged<double> onSeekChanged;
  final ValueChanged<double> onSeekEnd;
  final VoidCallback onSeekBack;
  final VoidCallback onTogglePlayPause;
  final VoidCallback onSeekForward;
  final VoidCallback onChangeSpeed;

  const BottomControls({
    super.key,
    required this.position,
    required this.duration,
    required this.playbackSpeed,
    required this.isPlaying,
    required this.formatTime,
    required this.onSeekChanged,
    required this.onSeekEnd,
    required this.onSeekBack,
    required this.onTogglePlayPause,
    required this.onSeekForward,
    required this.onChangeSpeed,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            trackHeight: 4,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
            activeTrackColor: Colors.blue,
            inactiveTrackColor: Colors.grey.shade600,
            thumbColor: Colors.blue,
          ),
          child: Slider(
            value: position.inMilliseconds.toDouble().clamp(
                  0,
                  duration.inMilliseconds.toDouble()._max(1),
                ),
            max: duration.inMilliseconds.toDouble()._max(1),
            onChanged: onSeekChanged,
            onChangeEnd: onSeekEnd,
          ),
        ),
        Row(
          children: [
            Text(
              '${formatTime(position)} / ${formatTime(duration)}',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
            const Spacer(),
            IconButton(
              onPressed: onSeekBack,
              icon: const Icon(Icons.replay_10, color: Colors.white),
            ),
            IconButton(
              onPressed: onTogglePlayPause,
              icon: Icon(
                isPlaying ? Icons.pause : Icons.play_arrow,
                color: Colors.white,
                size: 32,
              ),
            ),
            IconButton(
              onPressed: onSeekForward,
              icon: const Icon(Icons.forward_10, color: Colors.white),
            ),
            const Spacer(),
            GestureDetector(
              onTap: onChangeSpeed,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  '${playbackSpeed}x',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

extension _DoubleExt on double {
  double _max(double other) => this > other ? this : other;
}
