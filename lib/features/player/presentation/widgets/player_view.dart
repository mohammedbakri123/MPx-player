import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import 'player_surface.dart';
import 'vlc_player_surface.dart';
import 'video_player_surface.dart';
import 'gesture_layer.dart';
import 'overlay_layer.dart';
import 'controls_layer.dart';
import '../../domain/player_type.dart';

class PlayerView extends StatelessWidget {
  final PlayerController controller;
  final String videoTitle;
  final VoidCallback onBack;
  final VoidCallback onSubtitleSettings;
  final VoidCallback onSettings;

  const PlayerView({
    super.key,
    required this.controller,
    required this.videoTitle,
    required this.onBack,
    required this.onSubtitleSettings,
    required this.onSettings,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Video player for $videoTitle',
      child: Stack(
        fit: StackFit.expand,
        children: [
          _buildPlayerSurface(),
          GestureLayer(controller: controller),
          OverlayLayer(controller: controller),
          ControlsLayer(
            controller: controller,
            title: videoTitle,
            onBack: onBack,
            onSubtitleSettings: onSubtitleSettings,
            onSettings: onSettings,
          ),
        ],
      ),
    );
  }

  Widget _buildPlayerSurface() {
    switch (controller.playerType) {
      case PlayerType.mediaKit:
        return PlayerSurface(
          controller: controller.videoController,
          subtitleFontSize: controller.subtitleFontSize,
          subtitleColor: controller.subtitleColor,
          subtitleHasBackground: controller.subtitleHasBackground,
          aspectRatioMode: controller.aspectRatioMode,
        );
      case PlayerType.vlc:
        return VlcPlayerSurface(
          controller: controller.vlcController,
          aspectRatioMode: controller.aspectRatioMode,
        );
      case PlayerType.videoPlayer:
        return VideoPlayerSurface(
          controller: controller.videoPlayerController,
          aspectRatioMode: controller.aspectRatioMode,
        );
    }
  }
}
