import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';

class GestureLayer extends StatelessWidget {
  final PlayerController controller;

  const GestureLayer({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // Left zone - brightness control
        Expanded(
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: controller.showControlsNow,
            onDoubleTap: controller.seekBack,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('left'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),

        // Center zone - seek control
        Expanded(
          flex: 2,
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: controller.showControlsNow,
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

        // Right zone - volume control
        Expanded(
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: controller.showControlsNow,
            onDoubleTap: controller.seekForward,
            onVerticalDragStart: (_) => controller.onVerticalDragStart('right'),
            onVerticalDragUpdate: (details) =>
                controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => controller.onVerticalDragEnd(),
            onLongPressStart: (_) => controller.onLongPressStart(),
            onLongPressEnd: (_) => controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),
      ],
    );
  }
}
