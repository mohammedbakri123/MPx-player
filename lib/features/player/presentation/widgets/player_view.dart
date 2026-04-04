import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
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
  final VoidCallback? onNext;
  final VoidCallback? onPrevious;
  final VoidCallback? onTogglePip;
  final bool showPipButton;
  final bool isInPipMode;

  const PlayerView({
    super.key,
    required this.controller,
    required this.videoTitle,
    required this.onBack,
    required this.onSubtitleSettings,
    required this.onSettings,
    this.onNext,
    this.onPrevious,
    this.onTogglePip,
    this.showPipButton = false,
    this.isInPipMode = false,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Video player for $videoTitle',
      child: Stack(
        fit: StackFit.expand,
        children: [
          Selector<PlayerController, _PlayerSurfaceConfig>(
            selector: (_, controller) => _PlayerSurfaceConfig(
              subtitleFontSize: controller.subtitleFontSize,
              subtitleColor: controller.subtitleColor,
              subtitleFontFamily: controller.subtitleFontFamily,
              subtitleHasBackground: controller.subtitleHasBackground,
              subtitleFontWeight: controller.subtitleFontWeight,
              subtitleBottomPadding: controller.subtitleBottomPadding,
              subtitleBackgroundOpacity: controller.subtitleBackgroundOpacity,
              aspectRatioMode: controller.aspectRatioMode,
            ),
            builder: (context, config, _) {
              return PlayerSurface(
                controller: controller.videoController,
                subtitleFontSize: config.subtitleFontSize,
                subtitleColor: config.subtitleColor,
                subtitleFontFamily: config.subtitleFontFamily,
                subtitleHasBackground: config.subtitleHasBackground,
                subtitleFontWeight: config.subtitleFontWeight,
                subtitleBottomPadding: config.subtitleBottomPadding,
                subtitleBackgroundOpacity: config.subtitleBackgroundOpacity,
                aspectRatioMode: config.aspectRatioMode,
              );
            },
          ),
          if (!isInPipMode)
            Selector<PlayerController, bool>(
              selector: (_, controller) => controller.isLocked,
              builder: (context, _, __) => GestureLayer(controller: controller),
            ),
          if (!isInPipMode)
            AnimatedBuilder(
              animation: controller,
              builder: (context, _) => OverlayLayer(controller: controller),
            ),
          if (!isInPipMode)
            AnimatedBuilder(
              animation: controller,
              builder: (context, _) => ControlsLayer(
                controller: controller,
                title: videoTitle,
                onBack: onBack,
                onSubtitleSettings: onSubtitleSettings,
                onSettings: onSettings,
                onNext: onNext,
                onPrevious: onPrevious,
                onTogglePip: onTogglePip,
                showPipButton: showPipButton,
              ),
            ),
        ],
      ),
    );
  }
}

class _PlayerSurfaceConfig {
  final double subtitleFontSize;
  final Color subtitleColor;
  final String subtitleFontFamily;
  final bool subtitleHasBackground;
  final FontWeight subtitleFontWeight;
  final double subtitleBottomPadding;
  final double subtitleBackgroundOpacity;
  final AspectRatioMode aspectRatioMode;

  const _PlayerSurfaceConfig({
    required this.subtitleFontSize,
    required this.subtitleColor,
    required this.subtitleFontFamily,
    required this.subtitleHasBackground,
    required this.subtitleFontWeight,
    required this.subtitleBottomPadding,
    required this.subtitleBackgroundOpacity,
    required this.aspectRatioMode,
  });

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is _PlayerSurfaceConfig &&
        other.subtitleFontSize == subtitleFontSize &&
        other.subtitleColor == subtitleColor &&
        other.subtitleFontFamily == subtitleFontFamily &&
        other.subtitleHasBackground == subtitleHasBackground &&
        other.subtitleFontWeight == subtitleFontWeight &&
        other.subtitleBottomPadding == subtitleBottomPadding &&
        other.subtitleBackgroundOpacity == subtitleBackgroundOpacity &&
        other.aspectRatioMode == aspectRatioMode;
  }

  @override
  int get hashCode => Object.hash(
        subtitleFontSize,
        subtitleColor,
        subtitleFontFamily,
        subtitleHasBackground,
        subtitleFontWeight,
        subtitleBottomPadding,
        subtitleBackgroundOpacity,
        aspectRatioMode,
      );
}
