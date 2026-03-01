import 'package:flutter/material.dart';
import 'package:flutter_vlc_player/flutter_vlc_player.dart';
import '../../controller/player_state.dart';

/// Video surface widget for VLC player.
///
/// This widget renders the video using flutter_vlc_player's VideoPlayer widget.
class VlcPlayerSurface extends StatelessWidget {
  final VlcPlayerController? controller;
  final AspectRatioMode aspectRatioMode;

  const VlcPlayerSurface({
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
    final videoWidget = _VlcVideoPlayer(controller!);

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

/// Custom widget to render VLC video.
///
/// This uses the VLC player's built-in widget for rendering.
class _VlcVideoPlayer extends StatelessWidget {
  final VlcPlayerController controller;

  const _VlcVideoPlayer(this.controller);

  @override
  Widget build(BuildContext context) {
    return VlcPlayer(
      controller: controller,
      aspectRatio: controller.value.aspectRatio,
    );
  }
}
