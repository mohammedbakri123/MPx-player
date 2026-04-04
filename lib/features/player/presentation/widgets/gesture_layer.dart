import 'package:flutter/material.dart';
import 'helpers/double_tap_seek_zone.dart';
import '../../controller/player_controller.dart';

/// Gesture layer - ALWAYS VISIBLE, handles all touch gestures.
/// This layer sits on top of the video and below the controls.
class GestureLayer extends StatelessWidget {
  final PlayerController controller;

  const GestureLayer({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 200 || constraints.maxHeight < 120) {
          return const SizedBox.shrink();
        }
        if (controller.isLocked) {
          return _buildLockedGestureLayer();
        }
        return _buildGestureLayer(context);
      },
    );
  }

  Widget _buildLockedGestureLayer() {
    return const IgnorePointer(
      ignoring: true,
      child: SizedBox.expand(),
    );
  }

  Widget _buildGestureLayer(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;

    return Row(
      children: [
        // Left zone - double tap seek back, brightness, horizontal seek, play/pause
        Expanded(
          child: DoubleTapSeekZone(
            controller: controller,
            direction: SeekDirection.back,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('left'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onHorizontalDragStart: (_) => controller.onHorizontalDragStart(0),
            onHorizontalDragUpdate: (details) => controller
                .onHorizontalDragUpdate(details.globalPosition.dx, screenWidth),
            onHorizontalDragEnd: (_) => controller.onHorizontalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
          ),
        ),
        // Center zone - single tap play/pause, horizontal seek
        Expanded(
          flex: 2,
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTapDown: (_) => controller.handleCenterTap(),
            onHorizontalDragStart: (details) =>
                controller.onHorizontalDragStart(details.globalPosition.dx),
            onHorizontalDragUpdate: (details) => controller
                .onHorizontalDragUpdate(details.globalPosition.dx, screenWidth),
            onHorizontalDragEnd: (_) => controller.onHorizontalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),
        // Right zone - double tap seek forward, volume, horizontal seek, play/pause
        Expanded(
          child: DoubleTapSeekZone(
            controller: controller,
            direction: SeekDirection.forward,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('right'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onHorizontalDragStart: (_) =>
                controller.onHorizontalDragStart(screenWidth),
            onHorizontalDragUpdate: (details) => controller
                .onHorizontalDragUpdate(details.globalPosition.dx, screenWidth),
            onHorizontalDragEnd: (_) => controller.onHorizontalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
          ),
        ),
      ],
    );
  }
}

enum SeekDirection { back, forward }
