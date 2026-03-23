import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class SubtitleFontOption {
  final String family;
  final String label;

  const SubtitleFontOption({required this.family, required this.label});
}

class SubtitleFontHelpers {
  static const List<SubtitleFontOption> options = [
    SubtitleFontOption(family: 'Roboto', label: 'Roboto'),
    SubtitleFontOption(family: 'Open Sans', label: 'Open Sans'),
    SubtitleFontOption(family: 'Lato', label: 'Lato'),
    SubtitleFontOption(family: 'Nunito Sans', label: 'Nunito Sans'),
    SubtitleFontOption(family: 'Merriweather Sans', label: 'Merriweather Sans'),
  ];

  static TextStyle textStyle(
    String family, {
    required Color color,
    required double fontSize,
    required FontWeight fontWeight,
    Color? backgroundColor,
    double? height,
    List<Shadow>? shadows,
  }) {
    return GoogleFonts.getFont(
      family,
      textStyle: TextStyle(
        color: color,
        fontSize: fontSize,
        fontWeight: fontWeight,
        backgroundColor: backgroundColor,
        height: height,
        shadows: shadows,
      ),
    );
  }
}
