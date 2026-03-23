import 'package:flutter/material.dart';
import '../helpers/subtitle_font_helpers.dart';

/// Preview widget showing how subtitles will appear with current settings
class SubtitlePreview extends StatelessWidget {
  final Color color;
  final double fontSize;
  final FontWeight fontWeight;
  final String fontFamily;
  final bool hasBackground;
  final double backgroundOpacity;
  final double bottomPadding;

  const SubtitlePreview({
    super.key,
    required this.color,
    required this.fontSize,
    required this.fontWeight,
    required this.fontFamily,
    required this.hasBackground,
    required this.backgroundOpacity,
    required this.bottomPadding,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF111827), Color(0xFF1F2937)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Preview',
            style: TextStyle(
              color: Colors.white70,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          SizedBox(height: bottomPadding * 0.35),
          Center(
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: hasBackground
                    ? Colors.black.withValues(alpha: backgroundOpacity)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                child: Text(
                  'This is how your subtitles will look',
                  textAlign: TextAlign.center,
                  style: SubtitleFontHelpers.textStyle(
                    fontFamily,
                    color: color,
                    fontSize: fontSize,
                    fontWeight: fontWeight,
                    height: 1.2,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
