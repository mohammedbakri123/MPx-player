import 'package:flutter/material.dart';
import '../../controller/player_state.dart';

class BottomControls extends StatelessWidget {
  final Duration position;
  final Duration duration;
  final double playbackSpeed;
  final bool isPlaying;
  final RepeatMode repeatMode;
  final String Function(Duration) formatTime;
  final ValueChanged<double> onSeekChanged;
  final ValueChanged<double> onSeekEnd;
  final VoidCallback onSeekBack;
  final VoidCallback onTogglePlayPause;
  final VoidCallback onSeekForward;
  final VoidCallback onToggleRepeat;
  final Function(BuildContext, double) onShowSpeedSheet;

  const BottomControls({
    super.key,
    required this.position,
    required this.duration,
    required this.playbackSpeed,
    required this.isPlaying,
    required this.repeatMode,
    required this.formatTime,
    required this.onSeekChanged,
    required this.onSeekEnd,
    required this.onSeekBack,
    required this.onTogglePlayPause,
    required this.onSeekForward,
    required this.onToggleRepeat,
    required this.onShowSpeedSheet,
  });

  IconData _getRepeatIcon() {
    switch (repeatMode) {
      case RepeatMode.off:
        return Icons.repeat;
      case RepeatMode.one:
        return Icons.repeat_one;
      case RepeatMode.all:
        return Icons.repeat;
    }
  }

  Color _getRepeatColor() {
    return repeatMode == RepeatMode.off ? Colors.white54 : Colors.blue;
  }

  void _onSpeedTap(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SpeedSelectionSheet(
        currentSpeed: playbackSpeed,
        onSpeedSelected: (speed) => onShowSpeedSheet(context, speed),
      ),
    );
  }

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
              onPressed: onToggleRepeat,
              icon: Icon(_getRepeatIcon(), color: _getRepeatColor()),
            ),
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
              onTap: () => _onSpeedTap(context),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
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

class _SpeedSelectionSheet extends StatelessWidget {
  final double currentSpeed;
  final ValueChanged<double> onSpeedSelected;

  const _SpeedSelectionSheet({
    required this.currentSpeed,
    required this.onSpeedSelected,
  });

  static const List<double> speeds = [
    0.25,
    0.5,
    0.75,
    1.0,
    1.25,
    1.5,
    1.75,
    2.0,
    2.5,
    3.0
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.only(top: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade600,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Playback Speed',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${currentSpeed}x',
                    style: const TextStyle(
                      color: Colors.blue,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const Divider(color: Colors.grey, height: 1),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 300),
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: speeds.length,
                itemBuilder: (context, index) {
                  final speed = speeds[index];
                  final isSelected = speed == currentSpeed;

                  return ListTile(
                    title: Text(
                      speed == 1.0 ? 'Normal' : '${speed}x',
                      style: TextStyle(
                        color: isSelected ? Colors.blue : Colors.white,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w600 : FontWeight.normal,
                      ),
                    ),
                    trailing: isSelected
                        ? const Icon(Icons.check, color: Colors.blue)
                        : null,
                    onTap: () {
                      onSpeedSelected(speed);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
