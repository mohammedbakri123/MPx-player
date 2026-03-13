import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class SplashSubtitle extends StatelessWidget {
  const SplashSubtitle({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      'Fast video browsing, clean playback',
      style: GoogleFonts.manrope(
        fontSize: 12,
        fontWeight: FontWeight.w700,
        color: theme.mutedText,
        letterSpacing: 1.8,
      ),
    );
  }
}
