import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../../controller/player_state.dart';

/// Video surface widget for video_player package.
///
/// This widget renders the video using video_player's VideoPlayer widget.
class VideoPlayerSurface extends StatelessWidget {
  final VideoPlayerController? controller;
  final AspectRatioMode aspectRatioMode;

  const VideoPlayerSurface({
    super.key,
    this.controller,
    this.aspectRatioMode = AspectRatioMode.fit,
  });

  @override
  Widget build(BuildContext context) {
    if (controller == null || !controller!.value.isInitialized) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    return Center(
      child: _buildVideoWithAspectRatio(),
    );
  }

  Widget _buildVideoWithAspectRatio() {
    final videoWidget = VideoPlayer(controller!);

    switch (aspectRatioMode) {
      case AspectRatioMode.fit:
        return _buildFitVideo(videoWidget);
      case AspectRatioMode.fill:
        return _buildFillVideo(videoWidget);
      case AspectRatioMode.stretch:
        return _buildStretchVideo(videoWidget);
      case AspectRatioMode.ratio16x9:
        return AspectRatio(aspectRatio: 16 / 9, child: videoWidget);
      case AspectRatioMode.ratio4x3:
        return AspectRatio(aspectRatio: 4 / 3, child: videoWidget);
    }
  }

  Widget _buildFitVideo(Widget videoWidget) {
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
          child: videoWidget,
        );
      },
    );
  }

  Widget _buildFillVideo(Widget videoWidget) {
    return ClipRect(
      child: FittedBox(
        fit: BoxFit.cover,
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SizedBox(
              width: constraints.maxWidth,
              height: constraints.maxHeight,
              child: videoWidget,
            );
          },
        ),
      ),
    );
  }

  Widget _buildStretchVideo(Widget videoWidget) {
    return SizedBox(
      width: double.infinity,
      height: double.infinity,
      child: videoWidget,
    );
  }
}
