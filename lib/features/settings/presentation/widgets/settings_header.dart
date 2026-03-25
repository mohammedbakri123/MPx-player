import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

class SettingsHeader extends StatelessWidget {
  const SettingsHeader({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      child: Text(
        'Settings',
        style: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w800,
          color: theme.strongText,
          letterSpacing: -0.8,
        ),
      ),
    );
  }
}
