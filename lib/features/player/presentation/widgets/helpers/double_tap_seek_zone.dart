import 'package:flutter/material.dart';
import 'package:mpx/features/player/controller/player_controller.dart';
import 'package:mpx/features/player/presentation/widgets/gesture_layer.dart';

class DoubleTapSeekZone extends StatefulWidget {
  final PlayerController controller;
  final SeekDirection direction;
  final void Function(DragStartDetails)? onVerticalDragStart;
  final void Function(DragUpdateDetails)? onVerticalDragUpdate;
  final void Function(DragEndDetails)? onVerticalDragEnd;
  final void Function(DragStartDetails)? onHorizontalDragStart;
  final void Function(DragUpdateDetails)? onHorizontalDragUpdate;
  final void Function(DragEndDetails)? onHorizontalDragEnd;
  final void Function(LongPressStartDetails)? onLongPressStart;
  final void Function(LongPressEndDetails)? onLongPressEnd;

  const DoubleTapSeekZone({
    super.key,
    required this.controller,
    required this.direction,
    this.onVerticalDragStart,
    this.onVerticalDragUpdate,
    this.onVerticalDragEnd,
    this.onHorizontalDragStart,
    this.onHorizontalDragUpdate,
    this.onHorizontalDragEnd,
    this.onLongPressStart,
    this.onLongPressEnd,
  });

  @override
  State<DoubleTapSeekZone> createState() => _DoubleTapSeekZoneState();
}

class _DoubleTapSeekZoneState extends State<DoubleTapSeekZone> {
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTapDown: (_) => widget.controller.handleCenterTap(),
      onVerticalDragStart: widget.onVerticalDragStart,
      onVerticalDragUpdate: widget.onVerticalDragUpdate,
      onVerticalDragEnd: widget.onVerticalDragEnd,
      onHorizontalDragStart: widget.onHorizontalDragStart,
      onHorizontalDragUpdate: widget.onHorizontalDragUpdate,
      onHorizontalDragEnd: widget.onHorizontalDragEnd,
      onLongPressStart: widget.onLongPressStart,
      onLongPressEnd: widget.onLongPressEnd,
      child: Container(color: Colors.transparent),
    );
  }
}
