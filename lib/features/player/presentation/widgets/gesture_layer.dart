import 'package:flutter/material.dart';
import 'helpers/double_tap_seek_zone.dart';
import '../../controller/player_controller.dart';

class GestureLayer extends StatelessWidget {
  final PlayerController controller;

  const GestureLayer({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    if (controller.isLocked) {
      return _buildLockedGestureLayer();
    }

    return Row(
      children: [
        Expanded(
          child: DoubleTapSeekZone(
            controller: controller,
            direction: SeekDirection.back,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('left'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
          ),
        ),
        Expanded(
          flex: 2,
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: controller.togglePlayPause,
            onHorizontalDragStart: (details) =>
                controller.onHorizontalDragStart(details.globalPosition.dx),
            onHorizontalDragUpdate: (details) =>
                controller.onHorizontalDragUpdate(
              details.globalPosition.dx,
              MediaQuery.of(context).size.width,
            ),
            onHorizontalDragEnd: (_) => controller.onHorizontalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),
        Expanded(
          child: DoubleTapSeekZone(
            controller: controller,
            direction: SeekDirection.forward,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('right'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
          ),
        ),
      ],
    );
  }

  Widget _buildLockedGestureLayer() {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: controller.unlock,
      child: Container(color: Colors.transparent),
    );
  }
}

enum SeekDirection { back, forward }
