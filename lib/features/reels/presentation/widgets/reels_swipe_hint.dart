import 'package:flutter/material.dart';

class ReelsSwipeHint extends StatelessWidget {
  final bool showExitHint;

  const ReelsSwipeHint({
    super.key,
    required this.showExitHint,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      opacity: showExitHint ? 1 : 0.72,
      duration: const Duration(milliseconds: 260),
      child: IgnorePointer(
        ignoring: true,
        child: Container(
          padding: const EdgeInsets.symmetric(
            horizontal: 14,
            vertical: 10,
          ),
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.48),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: Colors.white.withValues(alpha: 0.08),
            ),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.swipe_right_alt_rounded,
                color: Colors.white,
                size: 18,
              ),
              SizedBox(width: 8),
              Text(
                'Swipe right to leave Reels',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
