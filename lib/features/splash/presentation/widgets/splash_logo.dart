import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class SplashLogo extends StatelessWidget {
  const SplashLogo({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = theme.colorScheme.primary;

    return Container(
      width: 108,
      height: 108,
      margin: const EdgeInsets.only(bottom: 26),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            theme.elevatedSurface
                .withValues(alpha: theme.isDarkMode ? 0.88 : 0.94),
            primaryColor.withValues(alpha: theme.isDarkMode ? 0.14 : 0.08),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: theme.softBorder),
        boxShadow: [
          BoxShadow(
            color:
                primaryColor.withValues(alpha: theme.isDarkMode ? 0.20 : 0.14),
            blurRadius: 32,
            spreadRadius: -6,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(30),
        child: Image.asset(
          'assets/images/logo.png',
          width: 72,
          height: 72,
        ),
      ),
    );
  }
}
