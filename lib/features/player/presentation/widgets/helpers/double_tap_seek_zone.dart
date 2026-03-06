import 'package:flutter/material.dart';
import 'package:mpx/features/player/controller/player_controller.dart';
import 'package:mpx/features/player/presentation/widgets/gesture_layer.dart';

class DoubleTapSeekZone extends StatefulWidget {
  final PlayerController controller;
  final SeekDirection direction;
  final void Function(DragStartDetails)? onVerticalDragStart;
  final void Function(DragUpdateDetails)? onVerticalDragUpdate;
  final void Function(DragEndDetails)? onVerticalDragEnd;
  final void Function(LongPressStartDetails)? onLongPressStart;
  final void Function(LongPressEndDetails)? onLongPressEnd;

  const DoubleTapSeekZone({
    required this.controller,
    required this.direction,
    this.onVerticalDragStart,
    this.onVerticalDragUpdate,
    this.onVerticalDragEnd,
    this.onLongPressStart,
    this.onLongPressEnd,
  });

  @override
  State<DoubleTapSeekZone> createState() => _DoubleTapSeekZoneState();
}

class _DoubleTapSeekZoneState extends State<DoubleTapSeekZone> {
  void _handleTap() {
    widget.controller.handleDoubleTap(widget.direction);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: _handleTap,
      onVerticalDragStart: widget.onVerticalDragStart,
      onVerticalDragUpdate: widget.onVerticalDragUpdate,
      onVerticalDragEnd: widget.onVerticalDragEnd,
      onLongPressStart: widget.onLongPressStart,
      onLongPressEnd: widget.onLongPressEnd,
      child: Container(color: Colors.transparent),
    );
  }
}
