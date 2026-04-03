import 'package:flutter/material.dart';

class PauseIndicator extends StatelessWidget {
  final bool isPaused;

  const PauseIndicator({
    super.key,
    required this.isPaused,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      opacity: isPaused ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: AnimatedScale(
        scale: isPaused ? 1.0 : 0.6,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
        child: Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.45),
            shape: BoxShape.circle,
          ),
          child: Icon(
            isPaused ? Icons.pause_rounded : Icons.play_arrow_rounded,
            color: Colors.white,
            size: 40,
          ),
        ),
      ),
    );
  }
}
