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
    return Semantics(
      label: 'Video player for $videoTitle',
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Layer 1: Video surface
          PlayerSurface(
            controller: controller.videoController,
            subtitleFontSize: controller.subtitleFontSize,
            subtitleColor: controller.subtitleColor,
            subtitleHasBackground: controller.subtitleHasBackground,
            subtitleFontWeight: controller.subtitleFontWeight,
            subtitleBottomPadding: controller.subtitleBottomPadding,
            subtitleBackgroundOpacity: controller.subtitleBackgroundOpacity,
            aspectRatioMode: controller.aspectRatioMode,
          ),
          // Layer 2: Visual overlays (indicators, animations)
          OverlayLayer(controller: controller),
          // Layer 3: Gestures (ALWAYS VISIBLE - handles all touches)
          GestureLayer(controller: controller),
          // Layer 4: Controls (fade in/out, buttons intercept touches)
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
}
