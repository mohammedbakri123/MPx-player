import 'package:flutter/material.dart';

abstract final class AppTheme {
  static const Color seedColor = Color(0xFF6366F1);

  static ThemeData get light => _buildTheme(Brightness.light);
  static ThemeData get dark => _buildTheme(Brightness.dark);

  static ThemeData _buildTheme(Brightness brightness) {
    final bool isDark = brightness == Brightness.dark;

    final ColorScheme scheme = isDark
        ? const ColorScheme.dark(
            primary: Color(0xFF818CF8),
            onPrimary: Color(0xFF0F172A),
            primaryContainer: Color(0xFF312E81),
            onPrimaryContainer: Color(0xFFE0E7FF),
            secondary: Color(0xFF38BDF8),
            onSecondary: Color(0xFF082F49),
            secondaryContainer: Color(0xFF0369A1),
            onSecondaryContainer: Color(0xFFE0F2FE),
            tertiary: Color(0xFF34D399),
            onTertiary: Color(0xFF064E3B),
            tertiaryContainer: Color(0xFF065F46),
            onTertiaryContainer: Color(0xFFD1FAE5),
            surface: Color(0xFF0B0E17),
            onSurface: Color(0xFFF8FAFC),
            surfaceContainerLow: Color(0xFF101421),
            surfaceContainer: Color(0xFF151A2B),
            surfaceContainerHigh: Color(0xFF1C2237),
            surfaceContainerHighest: Color(0xFF232B45),
            outline: Color(0xFF475569),
            outlineVariant: Color(0xFF273147),
          )
        : const ColorScheme.light(
            primary: Color(0xFF4F46E5),
            onPrimary: Colors.white,
            primaryContainer: Color(0xFFEEF2FF),
            onPrimaryContainer: Color(0xFF312E81),
            secondary: Color(0xFF0284C7),
            onSecondary: Colors.white,
            secondaryContainer: Color(0xFFE0F2FE),
            onSecondaryContainer: Color(0xFF0369A1),
            tertiary: Color(0xFF059669),
            onTertiary: Colors.white,
            tertiaryContainer: Color(0xFFD1FAE5),
            onTertiaryContainer: Color(0xFF065F46),
            surface: Color(0xFFF8FAFC),
            onSurface: Color(0xFF0F172A),
            surfaceContainerLow: Color(0xFFFFFFFF),
            surfaceContainer: Color(0xFFF1F5F9),
            surfaceContainerHigh: Color(0xFFE2E8F0),
            surfaceContainerHighest: Color(0xFFCBD5E1),
            outline: Color(0xFF94A3B8),
            outlineVariant: Color(0xFFE2E8F0),
          );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      appBarTheme: AppBarTheme(
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
          color: scheme.onSurface,
        ),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: isDark ? 0.4 : 0.8),
          ),
        ),
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        side: BorderSide(
          color: scheme.outlineVariant.withValues(alpha: isDark ? 0.3 : 0.6),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerHigh.withValues(alpha: isDark ? 0.4 : 0.6),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: isDark ? 0.25 : 0.5),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.primary, width: 1.5),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainer,
        elevation: 6,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: scheme.surfaceContainer,
        elevation: 8,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
      ),
    );
  }
}
