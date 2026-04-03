import 'package:flutter/material.dart';

class ReelProgressBar extends StatefulWidget {
  final Duration position;
  final Duration duration;
  final ValueChanged<Duration>? onSeek;

  const ReelProgressBar({
    super.key,
    required this.position,
    required this.duration,
    this.onSeek,
  });

  @override
  State<ReelProgressBar> createState() => _ReelProgressBarState();
}

class _ReelProgressBarState extends State<ReelProgressBar> {
  bool _isDragging = false;
  Duration? _dragPosition;

  double get _progress {
    if (widget.duration.inMilliseconds <= 0) return 0;
    final pos =
        _isDragging && _dragPosition != null ? _dragPosition! : widget.position;
    return pos.inMilliseconds / widget.duration.inMilliseconds;
  }

  String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  void _onPanUpdate(DragUpdateDetails details, BuildContext context) {
    if (widget.onSeek == null || widget.duration.inMilliseconds <= 0) return;
    final box = context.findRenderObject() as RenderBox?;
    if (box == null) return;
    final width = box.size.width;
    final localDx = details.localPosition.dx.clamp(0.0, width);
    final fraction = localDx / width;
    final seekTo = Duration(
        milliseconds: (fraction * widget.duration.inMilliseconds).round());
    setState(() {
      _dragPosition = seekTo;
    });
  }

  void _onPanEnd(DragEndDetails details) {
    if (_dragPosition != null && widget.onSeek != null) {
      widget.onSeek!(_dragPosition!);
    }
    setState(() {
      _isDragging = false;
      _dragPosition = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final position =
        _isDragging && _dragPosition != null ? _dragPosition! : widget.position;

    return GestureDetector(
      onHorizontalDragStart: (_) => setState(() => _isDragging = true),
      onHorizontalDragUpdate: (d) => _onPanUpdate(d, context),
      onHorizontalDragEnd: _onPanEnd,
      onTapDown: (details) {
        if (widget.onSeek == null || widget.duration.inMilliseconds <= 0) {
          return;
        }
        final box = context.findRenderObject() as RenderBox?;
        if (box == null) return;
        final width = box.size.width;
        final fraction = details.localPosition.dx.clamp(0.0, width) / width;
        final seekTo = Duration(
            milliseconds: (fraction * widget.duration.inMilliseconds).round());
        widget.onSeek!(seekTo);
      },
      child: Container(
        height: 24,
        alignment: Alignment.center,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 3,
              width: double.infinity,
              margin: const EdgeInsets.symmetric(horizontal: 12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(2),
              ),
              child: Stack(
                children: [
                  FractionallySizedBox(
                    alignment: Alignment.centerLeft,
                    widthFactor: _progress.clamp(0.0, 1.0),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if (_isDragging || widget.duration.inMilliseconds > 0)
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  '${_formatDuration(position)} / ${_formatDuration(widget.duration)}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w500,
                    letterSpacing: 0.3,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
