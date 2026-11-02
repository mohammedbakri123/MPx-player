import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../../../core/theme/mpx_ui_tokens.dart';

/// Full player screen, presentation only.
///
/// This is a UI shell: every value is a prop and every control is a
/// callback prop. There is no playback engine, timer, or controller
/// logic here, on purpose — the engine decision is still open in
/// ARCHITECTURE.md, and this screen must be wireable later without
/// a redesign.
///
/// MX-inspired layout, in MPx's own code: the player chrome is
/// black even in light mode, video gets a wide 16:9 black area
/// (audio keeps a square artwork tile), and the first-open
/// gesture hint is a single dismissible line, not a tutorial.
/// Gestures: double-tap the artwork toggles play/pause, and a
/// horizontal drag on the artwork seeks. Brightness / volume
/// swipes are deliberately NOT faked here — they need a real
/// engine and system controls, which do not exist yet.
class PlayerScreen extends StatefulWidget {
  const PlayerScreen({
    super.key,
    required this.title,
    required this.subtitle,
    required this.isAudio,
    required this.isPlaying,
    required this.progress,
    required this.positionLabel,
    required this.durationLabel,
    this.upNextTitles = const <String>[],
    required this.onClose,
    required this.onTogglePlay,
    required this.onPrevious,
    required this.onNext,
    required this.onSeek,
  });

  final String title;
  final String subtitle;
  final bool isAudio;
  final bool isPlaying;
  final double progress;
  final String positionLabel;
  final String durationLabel;
  final List<String> upNextTitles;
  final VoidCallback onClose;
  final VoidCallback onTogglePlay;
  final VoidCallback onPrevious;
  final VoidCallback onNext;
  final ValueChanged<double> onSeek;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  bool _showGestureHint = true;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final List<Color> gradient = widget.isAudio
        ? theme.folderMusicGradient
        : theme.folderVideoGradient;
    const Color onBlack = Colors.white;
    const Color onBlackMuted = Colors.white70;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: onBlack,
        leading: IconButton(
          icon: const Icon(Icons.keyboard_arrow_down_rounded),
          tooltip: 'Close player',
          onPressed: widget.onClose,
        ),
        title: const Text('Now Playing'),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.more_vert_rounded),
            tooltip: 'More options',
            onPressed: () {},
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(
            horizontal: MpxSpacing.page,
            vertical: MpxSpacing.lg,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              // Gesture hint: one line, shown on first open,
              // dismissed with the close button. Not a tutorial.
              if (_showGestureHint)
                Container(
                  margin: const EdgeInsets.only(bottom: MpxSpacing.lg),
                  padding: const EdgeInsets.symmetric(
                    horizontal: MpxSpacing.md,
                    vertical: MpxSpacing.sm,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white10,
                    borderRadius:
                        BorderRadius.circular(MpxRadius.lg),
                  ),
                  child: Row(
                    children: <Widget>[
                      const Icon(
                        Icons.touch_app_rounded,
                        color: onBlackMuted,
                        size: 18,
                      ),
                      const SizedBox(width: MpxSpacing.sm),
                      const Expanded(
                        child: Text(
                          'Double-tap to play/pause • Drag sideways to seek',
                          style: TextStyle(
                            fontSize: 12,
                            color: onBlackMuted,
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded, size: 18),
                        color: onBlackMuted,
                        tooltip: 'Dismiss hint',
                        constraints: const BoxConstraints(
                          minWidth: 32,
                          minHeight: 32,
                        ),
                        padding: EdgeInsets.zero,
                        onPressed: () {
                          setState(() {
                            _showGestureHint = false;
                          });
                        },
                      ),
                    ],
                  ),
                ),
              // Video area / artwork: MX-style gesture surface.
              // Video is a wide black area; audio keeps the
              // square gradient artwork tile.
              GestureDetector(
                onDoubleTap: widget.onTogglePlay,
                onHorizontalDragUpdate: (DragUpdateDetails details) {
                  final double width = MediaQuery.of(context).size.width;
                  final double next =
                      (widget.progress + (details.primaryDelta ?? 0) / width * 0.5)
                          .clamp(0.0, 1.0);
                  widget.onSeek(next);
                },
                child: AspectRatio(
                  aspectRatio: widget.isAudio ? 1 : 16 / 9,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: widget.isAudio
                          ? LinearGradient(
                              colors: gradient,
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                            )
                          : null,
                      color: widget.isAudio ? null : Colors.black,
                      borderRadius:
                          BorderRadius.circular(MpxRadius.artwork),
                      border: widget.isAudio
                          ? null
                          : Border.all(color: Colors.white12),
                      boxShadow: widget.isAudio
                          ? <BoxShadow>[
                              BoxShadow(
                                color: theme.cardShadow,
                                blurRadius: 32,
                                offset: const Offset(0, 12),
                              ),
                            ]
                          : null,
                    ),
                    child: Icon(
                      widget.isAudio
                          ? Icons.music_note_rounded
                          : Icons.videocam_rounded,
                      color: Colors.white,
                      size: widget.isAudio ? 96 : 64,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: MpxSpacing.xxl),
              Text(
                widget.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.5,
                  color: onBlack,
                ),
              ),
              const SizedBox(height: MpxSpacing.sm),
              Text(
                widget.subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 14,
                  color: onBlackMuted,
                ),
              ),
              const SizedBox(height: MpxSpacing.xxl),
              // Progress: visual only, value and seek are props.
              Slider(
                value: widget.progress.clamp(0.0, 1.0),
                onChanged: widget.onSeek,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: <Widget>[
                  Text(
                    widget.positionLabel,
                    style: const TextStyle(
                      fontSize: 12,
                      color: onBlackMuted,
                    ),
                  ),
                  Text(
                    widget.durationLabel,
                    style: const TextStyle(
                      fontSize: 12,
                      color: onBlackMuted,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: MpxSpacing.lg),
              // Controls.
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[
                  IconButton(
                    icon: const Icon(Icons.shuffle_rounded),
                    color: onBlack,
                    tooltip: 'Shuffle',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: () {},
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_previous_rounded),
                    color: onBlack,
                    tooltip: 'Previous',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: widget.onPrevious,
                  ),
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: gradient,
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: Icon(
                        widget.isPlaying
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                        color: Colors.white,
                        size: 36,
                      ),
                      tooltip: widget.isPlaying ? 'Pause' : 'Play',
                      onPressed: widget.onTogglePlay,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_next_rounded),
                    color: onBlack,
                    tooltip: 'Next',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: widget.onNext,
                  ),
                  IconButton(
                    icon: const Icon(Icons.repeat_rounded),
                    color: onBlack,
                    tooltip: 'Repeat',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: () {},
                  ),
                ],
              ),
              const SizedBox(height: MpxSpacing.xxl),
              if (widget.upNextTitles.isNotEmpty) ...<Widget>[
                const Text(
                  'UP NEXT',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: onBlackMuted,
                  ),
                ),
                const SizedBox(height: MpxSpacing.sm),
                for (final String upNext in widget.upNextTitles)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius:
                            BorderRadius.circular(MpxRadius.md),
                      ),
                      child: const Icon(
                        Icons.music_note_rounded,
                        color: onBlackMuted,
                      ),
                    ),
                    title: Text(
                      upNext,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: onBlack),
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
