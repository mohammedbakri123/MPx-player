import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class SplashProgressBar extends StatelessWidget {
  final Animation<double> animation;

  const SplashProgressBar({
    super.key,
    required this.animation,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = Theme.of(context).colorScheme.primary;

    return Center(
      child: AnimatedBuilder(
        animation: animation,
        builder: (context, child) {
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 192,
                height: 6,
                decoration: BoxDecoration(
                  color: theme.subtleSurface,
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: theme.softBorder),
                ),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Container(
                    width: 192 * animation.value,
                    height: 6,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          primaryColor,
                          Theme.of(context).colorScheme.secondary,
                        ],
                      ),
                      borderRadius: BorderRadius.circular(999),
                      boxShadow: [
                        BoxShadow(
                          color: primaryColor.withValues(alpha: 0.32),
                          blurRadius: 12,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'Loading your library shell',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: theme.mutedText,
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
