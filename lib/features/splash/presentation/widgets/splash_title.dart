import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class SplashTitle extends StatelessWidget {
  const SplashTitle({super.key});

  @override
  Widget build(BuildContext context) {
    final primaryColor = Theme.of(context).colorScheme.primary;

    return RichText(
      text: TextSpan(
        children: [
          TextSpan(
            text: 'MP',
            style: GoogleFonts.inter(
              fontSize: 40,
              fontWeight: FontWeight.bold,
              color: Colors.white,
              letterSpacing: -1,
            ),
          ),
          TextSpan(
            text: 'x',
            style: GoogleFonts.inter(
              fontSize: 40,
              fontWeight: FontWeight.bold,
              color: primaryColor,
              letterSpacing: -1,
            ),
          ),
        ],
      ),
    );
  }
}
