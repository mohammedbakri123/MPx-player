import 'package:flutter/material.dart';

class SplashBackground extends StatelessWidget {
  const SplashBackground({super.key});

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        // Background Image
        Image.asset(
          'assets/images/splash-bg.png',
          fit: BoxFit.cover,
        ),
        // Dark Overlay
        Container(
          color: Colors.black.withValues(alpha: 0.6),
        ),
        // Blur effect overlay
        Container(
          color: Colors.black.withValues(alpha: 0.1),
        ),
      ],
    );
  }
}
