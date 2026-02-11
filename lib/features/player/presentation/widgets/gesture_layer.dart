import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';

class GestureLayer extends StatefulWidget {
  final PlayerController controller;

  const GestureLayer({
    super.key,
    required this.controller,
  });

  @override
  State<GestureLayer> createState() => _GestureLayerState();
}

class _GestureLayerState extends State<GestureLayer> {
  DateTime? _lastTapTime;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // Left zone - brightness control
        Expanded(
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () {
              // Handle double-tap vs single tap
              final now = DateTime.now();
              if (_lastTapTime == null ||
                  now.difference(_lastTapTime!) > const Duration(milliseconds: 300)) {
                // First tap, record the time
                _lastTapTime = now;
                widget.controller.showControlsNow();
              } else {
                // Double tap - seek back
                widget.controller.seekBack();
                _lastTapTime = null;
              }
            },
            onDoubleTap: () {
              // Explicit double-tap handler for seeking back
              widget.controller.seekBack();
              _lastTapTime = null;
            },
            onVerticalDragStart: (_) => widget.controller.onVerticalDragStart('left'),
            onVerticalDragUpdate: (details) =>
                widget.controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => widget.controller.onVerticalDragEnd(),
            onLongPressStart: (_) => widget.controller.onLongPressStart(),
            onLongPressEnd: (_) => widget.controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),

        // Center zone - play/pause and seek control
        Expanded(
          flex: 2,
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () {
              // Toggle play/pause with single tap in center
              widget.controller.togglePlayPause();
            },
            onDoubleTap: () {
              // Toggle play/pause with double tap in center too
              widget.controller.togglePlayPause();
            },
            onHorizontalDragStart: (details) =>
                widget.controller.onHorizontalDragStart(details.globalPosition.dx),
            onHorizontalDragUpdate: (details) =>
                widget.controller.onHorizontalDragUpdate(
              details.globalPosition.dx,
              MediaQuery.of(context).size.width,
            ),
            onHorizontalDragEnd: (_) => widget.controller.onHorizontalDragEnd(),
            onLongPressStart: (_) => widget.controller.onLongPressStart(),
            onLongPressEnd: (_) => widget.controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),

        // Right zone - volume control
        Expanded(
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () {
              // Handle double-tap vs single tap
              final now = DateTime.now();
              if (_lastTapTime == null ||
                  now.difference(_lastTapTime!) > const Duration(milliseconds: 300)) {
                // First tap, record the time
                _lastTapTime = now;
                widget.controller.showControlsNow();
              } else {
                // Double tap - seek forward
                widget.controller.seekForward();
                _lastTapTime = null;
              }
            },
            onDoubleTap: () {
              // Explicit double-tap handler for seeking forward
              widget.controller.seekForward();
              _lastTapTime = null;
            },
            onVerticalDragStart: (_) => widget.controller.onVerticalDragStart('right'),
            onVerticalDragUpdate: (details) =>
                widget.controller.onVerticalDragUpdate(details.delta.dy),
            onVerticalDragEnd: (_) => widget.controller.onVerticalDragEnd(),
            onLongPressStart: (_) => widget.controller.onLongPressStart(),
            onLongPressEnd: (_) => widget.controller.onLongPressEnd(),
            child: Container(color: Colors.transparent),
          ),
        ),
      ],
    );
  }
}
