import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class SplashSubtitle extends StatelessWidget {
  const SplashSubtitle({super.key});

  @override
  Widget build(BuildContext context) {
    return Text(
      'CINEMATIC EXPERIENCE',
      style: GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        color: Colors.white.withValues(alpha: 0.5),
        letterSpacing: 3,
      ),
    );
  }
}
