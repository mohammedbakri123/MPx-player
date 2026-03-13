import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class SplashBackground extends StatelessWidget {
  const SplashBackground({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final accent = theme.colorScheme.primary;
    final secondary = theme.colorScheme.secondary;

    return Stack(
      fit: StackFit.expand,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                theme.appBackground,
                theme.appBackgroundAlt,
                theme.elevatedSurface,
              ],
            ),
          ),
        ),
        Positioned(
          top: -80,
          right: -40,
          child: _GlowOrb(
            size: 220,
            color: accent.withValues(alpha: theme.isDarkMode ? 0.18 : 0.14),
          ),
        ),
        Positioned(
          bottom: -100,
          left: -30,
          child: _GlowOrb(
            size: 260,
            color: secondary.withValues(alpha: theme.isDarkMode ? 0.14 : 0.10),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.transparent,
                  Colors.black
                      .withValues(alpha: theme.isDarkMode ? 0.14 : 0.04),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _GlowOrb extends StatelessWidget {
  final double size;
  final Color color;

  const _GlowOrb({required this.size, required this.color});

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: color,
              blurRadius: size * 0.5,
              spreadRadius: size * 0.05,
            ),
          ],
        ),
      ),
    );
  }
}
