import 'package:flutter/material.dart';

extension AppThemeTokens on ThemeData {
  bool get isDarkMode => brightness == Brightness.dark;

  Color get appBackground => colorScheme.surface;

  Color get appBackgroundAlt =>
      isDarkMode ? const Color(0xFF0B1422) : const Color(0xFFEAEDE3);

  Color get elevatedSurface =>
      isDarkMode ? const Color(0xFF0E1728) : Colors.white;

  Color get subtleSurface => isDarkMode
      ? Colors.white.withValues(alpha: 0.05)
      : const Color(0xFFF8FAFC);

  Color get softBorder => isDarkMode
      ? Colors.white.withValues(alpha: 0.08)
      : const Color(0xFFE2E8F0);

  Color get strongText => colorScheme.onSurface;

  Color get mutedText =>
      colorScheme.onSurface.withValues(alpha: isDarkMode ? 0.72 : 0.64);

  Color get faintText =>
      colorScheme.onSurface.withValues(alpha: isDarkMode ? 0.56 : 0.48);

  Color get cardShadow =>
      Colors.black.withValues(alpha: isDarkMode ? 0.18 : 0.05);
}
