import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/player/presentation/widgets/player_volume_indicator.dart';
import 'dart:async';

class ReelPlayerItem extends StatefulWidget {
  final VideoFile video;
  final bool isCurrentlyVisible;

  const ReelPlayerItem({
    super.key,
    required this.video,
    required this.isCurrentlyVisible,
  });

  @override
  State<ReelPlayerItem> createState() => _ReelPlayerItemState();
}

class _ReelPlayerItemState extends State<ReelPlayerItem> {
  late final Player _player = Player();
  late final VideoController _controller = VideoController(_player);
  bool _isMuted = false;
  Timer? _hideVolumeIndicatorTimer;
  double _volumeIndicatorOpacity = 0.0;
  bool _showSeekIndicator = false;
  bool _isSeekingForward = false;

  @override
  void initState() {
    super.initState();
    _player.setVolume(_isMuted ? 0.0 : 100.0);
    _player.setPlaylistMode(PlaylistMode.single);
    _player.open(Media(widget.video.path), play: widget.isCurrentlyVisible);
  }

  @override
  void didUpdateWidget(ReelPlayerItem oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isCurrentlyVisible != oldWidget.isCurrentlyVisible) {
      if (widget.isCurrentlyVisible) {
        _player.play();
      } else {
        _player.pause();
        // optionally seek back to start
        // _player.seek(Duration.zero);
      }
    }
  }

  @override
  void dispose() {
    _player.dispose();
    _hideVolumeIndicatorTimer?.cancel();
    super.dispose();
  }

  void _toggleMute() {
    setState(() {
      _isMuted = !_isMuted;
      _player.setVolume(_isMuted ? 0.0 : 100.0);
      _volumeIndicatorOpacity = 1.0;
      _hideVolumeIndicatorTimer?.cancel();
      _hideVolumeIndicatorTimer = Timer(const Duration(seconds: 1), () {
        if (mounted) {
          setState(() {
            _volumeIndicatorOpacity = 0.0;
          });
        }
      });
    });
  }

  void _seek(bool forward) {
    setState(() {
      _showSeekIndicator = true;
      _isSeekingForward = forward;
    });

    final currentPosition = _player.state.position;
    final newPosition = currentPosition +
        (forward ? const Duration(seconds: 10) : const Duration(seconds: -10));
    _player.seek(newPosition);

    Timer(const Duration(milliseconds: 500), () {
      if (mounted) {
        setState(() {
          _showSeekIndicator = false;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: _toggleMute,
      onDoubleTapDown: (details) {
        final screenWidth = MediaQuery.of(context).size.width;
        if (details.localPosition.dx < screenWidth / 2) {
          _seek(false); // Double tap on left half
        } else {
          _seek(true); // Double tap on right half
        }
      },
      child: Stack(
        children: [
          SizedBox(
            width: MediaQuery.of(context).size.width,
            height: MediaQuery.of(context).size.height,
            child: Video(
              controller: _controller,
              fit: BoxFit.contain, // Maintain aspect ratio without cropping
            ),
          ),
          Center(
            child: AnimatedOpacity(
              opacity: _volumeIndicatorOpacity,
              duration: const Duration(milliseconds: 300),
              child: PlayerVolumeIndicator(isMuted: _isMuted),
            ),
          ),
          if (_showSeekIndicator)
            Center(
              child: Icon(
                _isSeekingForward
                    ? Icons.forward_10_rounded
                    : Icons.replay_10_rounded,
                color: Colors.white,
                size: 80,
              ),
            ),
          // Progress bar at the bottom
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: StreamBuilder<Duration>(
              stream: _player.stream.position,
              builder: (context, snapshot) {
                final position = snapshot.data ?? Duration.zero;
                final duration = _player.state.duration;
                final double progress = duration.inMilliseconds > 0
                    ? position.inMilliseconds / duration.inMilliseconds
                    : 0.0;
                return LinearProgressIndicator(
                  value: progress,
                  backgroundColor: Colors.white.withValues(alpha: 0.3),
                  valueColor: AlwaysStoppedAnimation<Color>(
                    Theme.of(context).colorScheme.primary,
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
