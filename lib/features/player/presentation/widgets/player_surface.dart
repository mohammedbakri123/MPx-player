import 'package:flutter/material.dart';
import 'package:media_kit_video/media_kit_video.dart';
import '../../controller/player_state.dart';

class PlayerSurface extends StatelessWidget {
  final VideoController controller;
  final double subtitleFontSize;
  final Color subtitleColor;
  final bool subtitleHasBackground;
  final AspectRatioMode aspectRatioMode;

  const PlayerSurface({
    super.key,
    required this.controller,
    required this.subtitleFontSize,
    required this.subtitleColor,
    required this.subtitleHasBackground,
    this.aspectRatioMode = AspectRatioMode.fit,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: _buildVideoWithAspectRatio(),
    );
  }

  Widget _buildVideoWithAspectRatio() {
    final video = Video(
      controller: controller,
      controls: null,
      subtitleViewConfiguration: SubtitleViewConfiguration(
        style: TextStyle(
          fontSize: subtitleFontSize,
          color: subtitleColor,
          fontWeight: FontWeight.w500,
          backgroundColor: subtitleHasBackground
              ? Colors.black.withValues(alpha: 0.7)
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
        padding: const EdgeInsets.all(24.0),
      ),
    );

    switch (aspectRatioMode) {
      case AspectRatioMode.fit:
        return _buildFitVideo(video);
      case AspectRatioMode.fill:
        return _buildFillVideo(video);
      case AspectRatioMode.stretch:
        return _buildStretchVideo(video);
      case AspectRatioMode.ratio16x9:
        return AspectRatio(aspectRatio: 16 / 9, child: video);
      case AspectRatioMode.ratio4x3:
        return AspectRatio(aspectRatio: 4 / 3, child: video);
    }
  }

  Widget _buildFitVideo(Widget video) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const videoAspect = 16 / 9;
        final width = constraints.maxWidth;
        final height = constraints.maxHeight;
        final containerAspect = width / height;

        double videoWidth;
        double videoHeight;

        if (containerAspect > videoAspect) {
          videoHeight = height;
          videoWidth = height * videoAspect;
        } else {
          videoWidth = width;
          videoHeight = width / videoAspect;
        }

        return SizedBox(
          width: videoWidth,
          height: videoHeight,
          child: video,
        );
      },
    );
  }

  Widget _buildFillVideo(Widget video) {
    return ClipRect(
      child: FittedBox(
        fit: BoxFit.cover,
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SizedBox(
              width: constraints.maxWidth,
              height: constraints.maxHeight,
              child: video,
            );
          },
        ),
      ),
    );
  }

  Widget _buildStretchVideo(Widget video) {
    return SizedBox(
      width: double.infinity,
      height: double.infinity,
      child: video,
    );
  }
}
