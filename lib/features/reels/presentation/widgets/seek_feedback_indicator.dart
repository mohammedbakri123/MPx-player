import 'package:flutter/material.dart';

class SeekFeedbackIndicator extends StatelessWidget {
  final bool isForward;

  const SeekFeedbackIndicator({
    super.key,
    required this.isForward,
  });

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
            isForward ? Icons.forward_10_rounded : Icons.replay_10_rounded,
            color: Colors.white,
            size: 28,
          ),
          const SizedBox(width: 6),
          const Text(
            '10s',
            style: TextStyle(
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
