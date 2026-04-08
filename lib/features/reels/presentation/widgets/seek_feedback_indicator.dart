import 'package:flutter/material.dart';

class SeekFeedbackIndicator extends StatelessWidget {
  final bool isForward;
  final int forwardedTime;

  const SeekFeedbackIndicator({
    super.key,
    required this.isForward,
    required this.forwardedTime,
  });

  IconData _iconForStep() {
    switch (forwardedTime) {
      case 5:
        return isForward ? Icons.forward_5_rounded : Icons.replay_5_rounded;
      case 10:
        return isForward ? Icons.forward_10_rounded : Icons.replay_10_rounded;
      case 30:
        return isForward ? Icons.forward_30_rounded : Icons.replay_30_rounded;
      default:
        return isForward
            ? Icons.keyboard_double_arrow_right_rounded
            : Icons.keyboard_double_arrow_left_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            _iconForStep(),
            color: Colors.white,
            size: 28,
          ),
          const SizedBox(width: 6),
          Text(
            '${forwardedTime}s',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
