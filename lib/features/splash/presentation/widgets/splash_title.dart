import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class SplashTitle extends StatelessWidget {
  const SplashTitle({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = theme.colorScheme.primary;

    return RichText(
      text: TextSpan(
        children: [
          TextSpan(
            text: 'MP',
            style: GoogleFonts.manrope(
              fontSize: 42,
              fontWeight: FontWeight.w800,
              color: theme.strongText,
              letterSpacing: -1.4,
            ),
          ),
          TextSpan(
            text: 'x',
            style: GoogleFonts.manrope(
              fontSize: 42,
              fontWeight: FontWeight.w800,
              color: primaryColor,
              letterSpacing: -1.4,
            ),
          ),
        ],
      ),
    );
  }
}
