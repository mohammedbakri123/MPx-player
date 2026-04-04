import 'dart:async';
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
  int _tapCount = 0;
  Timer? _tapTimer;

  void _handleTap() {
    _tapCount++;
    _tapTimer?.cancel();
    _tapTimer = Timer(const Duration(milliseconds: 300), () {
      if (_tapCount == 1) {
        widget.controller.toggleControlsVisibility();
      } else if (_tapCount >= 2) {
        if (widget.direction == SeekDirection.back) {
          widget.controller.seekBack();
        } else {
          widget.controller.seekForward();
        }
      }
      _tapCount = 0;
    });
  }

  @override
  void dispose() {
    _tapTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: _handleTap,
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
