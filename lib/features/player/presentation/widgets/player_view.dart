import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import 'player_surface.dart';
import 'gesture_layer.dart';
import 'overlay_layer.dart';
import 'controls_layer.dart';

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
    return Stack(
      fit: StackFit.expand,
      children: [
        // Video surface
        PlayerSurface(
          controller: controller.videoController,
          subtitleFontSize: controller.subtitleFontSize,
          subtitleColor: controller.subtitleColor,
          subtitleHasBackground: controller.subtitleHasBackground,
        ),

        // Gesture layer
        GestureLayer(controller: controller),

        // Overlay indicators
        OverlayLayer(controller: controller),

        // Controls layer
        ControlsLayer(
          controller: controller,
          title: videoTitle,
          onBack: onBack,
          onSubtitleSettings: onSubtitleSettings,
          onSettings: onSettings,
        ),
      ],
    );
  }
}
