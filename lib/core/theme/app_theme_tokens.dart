import 'package:flutter/material.dart';

extension AppThemeTokens on ThemeData {
  bool get isDarkMode => brightness == Brightness.dark;

  Color get appBackground =>
      isDarkMode ? const Color(0xFF090B10) : const Color(0xFFF6F8FC);

  Color get appBackgroundAlt =>
      isDarkMode ? const Color(0xFF0F1420) : const Color(0xFFEDF2F9);

  Color get elevatedSurface =>
      isDarkMode ? const Color(0xFF141A29) : const Color(0xFFFFFFFF);

  Color get surfaceHighlight =>
      isDarkMode ? const Color(0xFF1B2338) : const Color(0xFFF1F5F9);

  Color get subtleSurface => isDarkMode
      ? const Color(0x14FFFFFF)
      : const Color(0xFFEEF3FA);

  Color get softBorder => isDarkMode
      ? const Color(0x1FFFFFFF)
      : const Color(0xFFDCE4EE);

  Color get strongText =>
      isDarkMode ? const Color(0xFFF8FAFC) : const Color(0xFF0F172A);

  Color get mutedText =>
      isDarkMode ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

  Color get faintText =>
      isDarkMode ? const Color(0xFF64748B) : const Color(0xFF94A3B8);

  Color get cardShadow =>
      Colors.black.withValues(alpha: isDarkMode ? 0.35 : 0.06);

  Color get accentEmerald => const Color(0xFF10B981);
  Color get accentCyan => const Color(0xFF06B6D4);
  Color get accentRose => const Color(0xFFF43F5E);
  Color get accentAmber => const Color(0xFFF59E0B);
  Color get accentIndigo => const Color(0xFF6366F1);

  List<Color> get folderMusicGradient => isDarkMode
      ? const <Color>[Color(0xFF4F46E5), Color(0xFF7C3AED)]
      : const <Color>[Color(0xFF6366F1), Color(0xFF8B5CF6)];

  List<Color> get folderVideoGradient => isDarkMode
      ? const <Color>[Color(0xFFE11D48), Color(0xFFF43F5E)]
      : const <Color>[Color(0xFFF43F5E), Color(0xFFFB7185)];

  List<Color> get folderPodcastGradient => isDarkMode
      ? const <Color>[Color(0xFF0284C7), Color(0xFF06B6D4)]
      : const <Color>[Color(0xFF0EA5E9), Color(0xFF22D3EE)];

  List<Color> get folderDownloadsGradient => isDarkMode
      ? const <Color>[Color(0xFF059669), Color(0xFF10B981)]
      : const <Color>[Color(0xFF10B981), Color(0xFF34D399)];

  List<Color> get folderDefaultGradient => isDarkMode
      ? const <Color>[Color(0xFF334155), Color(0xFF475569)]
      : const <Color>[Color(0xFF64748B), Color(0xFF94A3B8)];
}
