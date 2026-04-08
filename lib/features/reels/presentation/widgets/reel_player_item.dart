import 'package:flutter/material.dart';
import 'package:flutter_mpv/flutter_mpv.dart';
import 'package:flutter_mpv_video/flutter_mpv_video.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/player/presentation/widgets/player_volume_indicator.dart';
import 'package:mpx/features/reels/presentation/widgets/reel_progress_bar.dart';
import 'package:mpx/features/reels/presentation/widgets/seek_feedback_indicator.dart';
import 'package:mpx/features/reels/presentation/widgets/playback_speed_indicator.dart';
import 'package:mpx/features/reels/presentation/widgets/pause_indicator.dart';
import 'dart:async';

class ReelPlayerItem extends StatefulWidget {
  final VideoFile video;
  final bool isCurrentlyVisible;
  final double playbackSpeed;
  final VoidCallback? onTogglePause;
  final bool isPaused;

  const ReelPlayerItem({
    super.key,
    required this.video,
    required this.isCurrentlyVisible,
    this.playbackSpeed = 1.0,
    this.onTogglePause,
    this.isPaused = false,
  });

  @override
  State<ReelPlayerItem> createState() => _ReelPlayerItemState();
}

class _ReelPlayerItemState extends State<ReelPlayerItem> {
  late final Player _player = Player(
    configuration: const PlayerConfiguration(
      title: 'MPx Reels',
      bufferSize: 96 * 1024 * 1024,
      videoPerformance: VideoPerformancePresets.balanced,
    ),
  );
  late final VideoController _controller = VideoController(_player);
  bool _isMuted = false;
  Timer? _hideVolumeIndicatorTimer;
  double _volumeIndicatorOpacity = 0.0;
  bool _showSeekForward = false;
  bool _showSeekBackward = false;
  Timer? _seekHideTimer;
  bool _isLongPressing = false;
  bool _showPauseIndicator = false;
  Timer? _pauseHideTimer;
  Duration _currentPosition = Duration.zero;
  Duration _duration = Duration.zero;
  int _tapCount = 0;
  Timer? _tapTimer;

  @override
  void initState() {
    super.initState();
    _player.setVolume(_isMuted ? 0.0 : 100.0);
    _player.setPlaylistMode(PlaylistMode.single);
    _player.setRate(widget.playbackSpeed);
    _player.open(Media(widget.video.path),
        play: widget.isCurrentlyVisible && !widget.isPaused);
    _player.stream.position.listen((pos) {
      if (mounted) {
        setState(() => _currentPosition = pos);
      }
    });
    _player.stream.duration.listen((dur) {
      if (mounted) {
        setState(() => _duration = dur);
      }
    });
  }

  @override
  void didUpdateWidget(ReelPlayerItem oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.playbackSpeed != oldWidget.playbackSpeed && !_isLongPressing) {
      _player.setRate(widget.playbackSpeed);
    }
    if (widget.isCurrentlyVisible != oldWidget.isCurrentlyVisible ||
        widget.isPaused != oldWidget.isPaused) {
      final shouldPlay = widget.isCurrentlyVisible && !widget.isPaused;
      if (shouldPlay) {
        _player.play();
      } else {
        _player.pause();
      }
    }
  }

  @override
  void dispose() {
    _player.dispose();
    _hideVolumeIndicatorTimer?.cancel();
    _seekHideTimer?.cancel();
    _pauseHideTimer?.cancel();
    _tapTimer?.cancel();
    super.dispose();
  }

  void _onTap() {
    _tapCount++;
    if (_tapCount == 1) {
      _tapTimer = Timer(const Duration(milliseconds: 250), () {
        if (_tapCount == 1) {
          _toggleMute();
        }
        _tapCount = 0;
      });
    } else if (_tapCount >= 2) {
      _tapTimer?.cancel();
      _tapCount = 0;
      _togglePause();
    }
  }

  void _toggleMute() {
    setState(() {
      _isMuted = !_isMuted;
      _player.setVolume(_isMuted ? 0.0 : 100.0);
      _volumeIndicatorOpacity = 1.0;
      _hideVolumeIndicatorTimer?.cancel();
      _hideVolumeIndicatorTimer = Timer(const Duration(seconds: 1), () {
        if (mounted) {
          setState(() => _volumeIndicatorOpacity = 0.0);
        }
      });
    });
  }

  void _togglePause() {
    widget.onTogglePause?.call();
    _showPauseIndicator = true;
    _pauseHideTimer?.cancel();
    _pauseHideTimer = Timer(const Duration(milliseconds: 600), () {
      if (mounted) setState(() => _showPauseIndicator = false);
    });
  }

  void _seek(bool forward) {
    final currentPosition = _player.state.position;
    final newPosition = currentPosition +
        (forward ? const Duration(seconds: 5) : const Duration(seconds: -5));
    _player.seek(newPosition);

    setState(() {
      if (forward) {
        _showSeekForward = true;
      } else {
        _showSeekBackward = true;
      }
    });

    _seekHideTimer?.cancel();
    _seekHideTimer = Timer(const Duration(milliseconds: 600), () {
      if (mounted) {
        setState(() {
          _showSeekForward = false;
          _showSeekBackward = false;
        });
      }
    });
  }

  void _onSeek(Duration position) {
    _player.seek(position);
  }

  void _onLongPressStart() {
    setState(() {
      _isLongPressing = true;
    });
    _player.setRate(2.0);
  }

  void _onLongPressEnd() {
    setState(() {
      _isLongPressing = false;
    });
    _player.setRate(widget.playbackSpeed);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: _onTap,
      onDoubleTapDown: (details) {
        final screenWidth = MediaQuery.of(context).size.width;
        if (details.localPosition.dx < screenWidth / 2) {
          _seek(false);
        } else {
          _seek(true);
        }
      },
      onLongPressStart: (_) => _onLongPressStart(),
      onLongPressEnd: (_) => _onLongPressEnd(),
      onLongPressCancel: _onLongPressEnd,
      child: Stack(
        children: [
          SizedBox(
            width: MediaQuery.of(context).size.width,
            height: MediaQuery.of(context).size.height,
            child: Video(
              controller: _controller,
              controls: null,
              fit: BoxFit.contain,
            ),
          ),
          Center(
            child: AnimatedOpacity(
              opacity: _volumeIndicatorOpacity,
              duration: const Duration(milliseconds: 300),
              child: PlayerVolumeIndicator(isMuted: _isMuted),
            ),
          ),
          if (_showSeekForward)
            const Positioned(
              right: 24,
              top: 0,
              bottom: 80,
              child: Center(
                child: SeekFeedbackIndicator(
                  isForward: true,
                  forwardedTime: 5,
                ),
              ),
            ),
          if (_showSeekBackward)
            const Positioned(
              left: 24,
              top: 0,
              bottom: 80,
              child: Center(
                child: SeekFeedbackIndicator(
                  isForward: false,
                  forwardedTime: 5,
                ),
              ),
            ),
          if (_isLongPressing)
            const Positioned(
              top: 120,
              left: 0,
              right: 0,
              child: Center(
                child: PlaybackSpeedIndicator(speed: 2.0),
              ),
            ),
          Center(
            child: PauseIndicator(
              isPaused: widget.isPaused && _showPauseIndicator,
            ),
          ),
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: ReelProgressBar(
              position: _currentPosition,
              duration: _duration,
              onSeek: _onSeek,
            ),
          ),
        ],
      ),
    );
  }
}
