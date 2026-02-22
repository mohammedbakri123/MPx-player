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
    if (controller.isLocked) {
      return _buildLockedGestureLayer();
    }

    return Row(
      children: [
        Expanded(
          child: _DoubleTapSeekZone(
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
          child: _DoubleTapSeekZone(
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

class _DoubleTapSeekZone extends StatefulWidget {
  final PlayerController controller;
  final SeekDirection direction;
  final void Function(DragStartDetails)? onVerticalDragStart;
  final void Function(DragUpdateDetails)? onVerticalDragUpdate;
  final void Function(DragEndDetails)? onVerticalDragEnd;
  final void Function(LongPressStartDetails)? onLongPressStart;
  final void Function(LongPressEndDetails)? onLongPressEnd;

  const _DoubleTapSeekZone({
    required this.controller,
    required this.direction,
    this.onVerticalDragStart,
    this.onVerticalDragUpdate,
    this.onVerticalDragEnd,
    this.onLongPressStart,
    this.onLongPressEnd,
  });

  @override
  State<_DoubleTapSeekZone> createState() => _DoubleTapSeekZoneState();
}

class _DoubleTapSeekZoneState extends State<_DoubleTapSeekZone> {
  int _tapCount = 0;
  DateTime? _lastTapTime;

  void _handleTap() {
    final now = DateTime.now();

    if (_lastTapTime != null &&
        now.difference(_lastTapTime!) < const Duration(milliseconds: 300)) {
      _tapCount++;
      if (_tapCount == 2) {
        _performSeek();
        _tapCount = 0;
        _lastTapTime = null;
        return;
      }
    } else {
      _tapCount = 1;
    }

    _lastTapTime = now;

    Future.delayed(const Duration(milliseconds: 300), () {
      if (_tapCount == 1 && mounted) {
        widget.controller.showControlsNow();
        _tapCount = 0;
      }
    });
  }

  void _performSeek() {
    if (widget.direction == SeekDirection.back) {
      widget.controller.seekBack();
    } else {
      widget.controller.seekForward();
    }
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
