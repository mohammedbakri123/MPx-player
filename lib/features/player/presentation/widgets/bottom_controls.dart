import 'package:flutter/material.dart';

class BottomControls extends StatefulWidget {
  final Duration position;
  final Duration duration;
  final bool isPlaying;
  final bool isFullscreen;
  final bool isLocked;
  final String Function(Duration) formatTime;
  final VoidCallback onSeekStart;
  final ValueChanged<double> onSeekChanged;
  final ValueChanged<double> onSeekEnd;
  final VoidCallback onTogglePlayPause;
  final VoidCallback onToggleFullscreen;
  final VoidCallback onToggleLock;
  final VoidCallback? onTogglePip;
  final bool showPipButton;

  const BottomControls({
    super.key,
    required this.position,
    required this.duration,
    required this.isPlaying,
    required this.isFullscreen,
    required this.isLocked,
    required this.formatTime,
    required this.onSeekStart,
    required this.onSeekChanged,
    required this.onSeekEnd,
    required this.onTogglePlayPause,
    required this.onToggleFullscreen,
    required this.onToggleLock,
    this.onTogglePip,
    this.showPipButton = false,
  });

  @override
  State<BottomControls> createState() => _BottomControlsState();
}

class _BottomControlsState extends State<BottomControls> {
  double? _dragValue;

  double get _sliderMax => widget.duration.inMilliseconds.toDouble()._max(1);

  double get _sliderValue {
    final source = _dragValue ?? widget.position.inMilliseconds.toDouble();
    return source.clamp(0.0, _sliderMax);
  }

  Duration get _effectivePosition =>
      Duration(milliseconds: _sliderValue.round());

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 200 || constraints.maxHeight < 120) {
          return const SizedBox.shrink();
        }
        return _buildContent(context);
      },
    );
  }

  Widget _buildContent(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 10),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.52),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 3,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
              activeTrackColor: Colors.white,
              inactiveTrackColor: Colors.white24,
              thumbColor: Colors.white,
              overlayShape: SliderComponentShape.noOverlay,
            ),
            child: Slider(
              value: _sliderValue,
              max: _sliderMax,
              onChangeStart: (_) {
                widget.onSeekStart();
              },
              onChanged: (value) {
                setState(() => _dragValue = value);
                widget.onSeekChanged(value);
              },
              onChangeEnd: (value) {
                widget.onSeekEnd(value);
                setState(() => _dragValue = null);
              },
            ),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                widget.formatTime(_effectivePosition),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Text(
                widget.formatTime(widget.duration),
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.72),
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              _UtilityButton(
                icon: widget.isLocked ? Icons.lock : Icons.lock_open,
                onPressed: widget.onToggleLock,
                active: widget.isLocked,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _TransportButton(
                      icon: widget.isPlaying
                          ? Icons.pause_rounded
                          : Icons.play_arrow_rounded,
                      onPressed: widget.onTogglePlayPause,
                      emphasized: true,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _UtilityButton(
                icon: widget.isFullscreen
                    ? Icons.fullscreen_exit
                    : Icons.fullscreen,
                onPressed: widget.onToggleFullscreen,
              ),
              if (widget.showPipButton && widget.onTogglePip != null) ...[
                const SizedBox(width: 8),
                _UtilityButton(
                  icon: Icons.picture_in_picture_alt_rounded,
                  onPressed: widget.onTogglePip!,
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _TransportButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onPressed;
  final bool emphasized;

  const _TransportButton({
    required this.icon,
    required this.onPressed,
    this.emphasized = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: emphasized ? 52 : 44,
      height: emphasized ? 52 : 44,
      decoration: BoxDecoration(
        color: emphasized
            ? Colors.white.withValues(alpha: 0.18)
            : Colors.white.withValues(alpha: 0.08),
        shape: BoxShape.circle,
      ),
      child: IconButton(
        onPressed: onPressed,
        padding: EdgeInsets.zero,
        splashRadius: emphasized ? 24 : 20,
        iconSize: emphasized ? 30 : 22,
        icon: Icon(icon, color: Colors.white),
      ),
    );
  }
}

class _UtilityButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onPressed;
  final bool active;

  const _UtilityButton({
    required this.icon,
    required this.onPressed,
    this.active = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color: active
            ? Colors.white.withValues(alpha: 0.18)
            : Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
      ),
      child: IconButton(
        onPressed: onPressed,
        padding: EdgeInsets.zero,
        splashRadius: 20,
        iconSize: 20,
        icon: Icon(icon, color: Colors.white),
      ),
    );
  }
}

extension _DoubleExt on double {
  double _max(double other) => this > other ? this : other;
}
