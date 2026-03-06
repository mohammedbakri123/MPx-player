import 'package:flutter/material.dart';

/// A widget that shows a ripple effect at touch points.
/// Useful for providing visual feedback on gesture interactions.
class TouchRippleLayer extends StatefulWidget {
  final Widget child;
  final bool enabled;

  const TouchRippleLayer({
    super.key,
    required this.child,
    this.enabled = true,
  });

  @override
  State<TouchRippleLayer> createState() => _TouchRippleLayerState();
}

class _TouchRippleLayerState extends State<TouchRippleLayer> {
  final List<_RippleData> _ripples = [];

  void _addRipple(Offset localPosition) {
    if (!widget.enabled) return;

    setState(() {
      _ripples.add(_RippleData(
        position: localPosition,
        id: DateTime.now().millisecondsSinceEpoch,
      ));
    });

    // Remove ripple after animation completes
    Future.delayed(const Duration(milliseconds: 600), () {
      if (mounted) {
        setState(() {
          _ripples.removeWhere((r) => r.id == DateTime.now().millisecondsSinceEpoch);
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerDown: (event) => _addRipple(event.localPosition),
      behavior: HitTestBehavior.deferToChild,
      child: Stack(
        children: [
          widget.child,
          ..._ripples.map((ripple) => _RippleWidget(ripple.position)),
        ],
      ),
    );
  }
}

class _RippleData {
  final Offset position;
  final int id;

  _RippleData({required this.position, required this.id});
}

class _RippleWidget extends StatefulWidget {
  final Offset position;

  const _RippleWidget(this.position);

  @override
  State<_RippleWidget> createState() => _RippleWidgetState();
}

class _RippleWidgetState extends State<_RippleWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  late Animation<double> _opacityAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );

    _scaleAnimation = Tween<double>(begin: 0.0, end: 3.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );

    _opacityAnimation = Tween<double>(begin: 0.3, end: 0.0).animate(
      CurvedAnimation(parent: _controller, curve: const Interval(0.0, 1.0)),
    );

    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: widget.position.dx - 30,
      top: widget.position.dy - 30,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          return Opacity(
            opacity: _opacityAnimation.value,
            child: Transform.scale(
              scale: _scaleAnimation.value,
              child: Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.white.withValues(alpha: 0.3),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
