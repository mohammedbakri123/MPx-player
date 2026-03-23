import 'package:flutter/material.dart';
import 'package:flutter_mpv_video/flutter_mpv_video.dart';
import 'package:mpx/features/settings/presentation/helpers/subtitle_font_helpers.dart';
import '../../controller/player_state.dart';

class PlayerSurface extends StatelessWidget {
  final VideoController controller;
  final double subtitleFontSize;
  final Color subtitleColor;
  final String subtitleFontFamily;
  final bool subtitleHasBackground;
  final FontWeight subtitleFontWeight;
  final double subtitleBottomPadding;
  final double subtitleBackgroundOpacity;
  final AspectRatioMode aspectRatioMode;

  const PlayerSurface({
    super.key,
    required this.controller,
    required this.subtitleFontSize,
    required this.subtitleColor,
    required this.subtitleFontFamily,
    required this.subtitleHasBackground,
    required this.subtitleFontWeight,
    required this.subtitleBottomPadding,
    required this.subtitleBackgroundOpacity,
    this.aspectRatioMode = AspectRatioMode.fit,
  });

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Center(
        child: _buildVideoWithAspectRatio(),
      ),
    );
  }

  Widget _buildVideoWithAspectRatio() {
    final BoxFit fit;
    switch (aspectRatioMode) {
      case AspectRatioMode.fit:
        fit = BoxFit.contain;
        break;
      case AspectRatioMode.fill:
        fit = BoxFit.cover;
        break;
      case AspectRatioMode.stretch:
        fit = BoxFit.fill;
        break;
      case AspectRatioMode.ratio16x9:
      case AspectRatioMode.ratio4x3:
        fit = BoxFit.contain;
        break;
    }

    Widget video = Video(
      controller: controller,
      controls: null,
      fit: fit,
      filterQuality: FilterQuality.low,
      pauseUponEnteringBackgroundMode: false,
      wakelock: false,
      subtitleViewConfiguration: SubtitleViewConfiguration(
        style: SubtitleFontHelpers.textStyle(
          subtitleFontFamily,
          fontSize: subtitleFontSize,
          color: subtitleColor,
          fontWeight: subtitleFontWeight,
          backgroundColor: subtitleHasBackground
              ? Colors.black.withValues(alpha: subtitleBackgroundOpacity)
              : Colors.transparent,
          shadows: !subtitleHasBackground
              ? const [
                  Shadow(
                    blurRadius: 4,
                    color: Colors.black,
                    offset: Offset(2, 2),
                  ),
                ]
              : null,
        ),
        textAlign: TextAlign.center,
        padding: EdgeInsets.fromLTRB(24, 24, 24, subtitleBottomPadding),
      ),
    );

    switch (aspectRatioMode) {
      case AspectRatioMode.ratio16x9:
        return AspectRatio(aspectRatio: 16 / 9, child: video);
      case AspectRatioMode.ratio4x3:
        return AspectRatio(aspectRatio: 4 / 3, child: video);
      case AspectRatioMode.fit:
      case AspectRatioMode.fill:
      case AspectRatioMode.stretch:
        return SizedBox.expand(child: video);
    }
  }
}
