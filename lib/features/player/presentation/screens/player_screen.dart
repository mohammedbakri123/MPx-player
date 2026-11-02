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
/// Not wired into navigation yet: opening it from the mini player
/// dock is a separate, deliberate step once the shell is approved.
class PlayerScreen extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final List<Color> gradient = isAudio
        ? theme.folderMusicGradient
        : theme.folderVideoGradient;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.keyboard_arrow_down_rounded),
          tooltip: 'Close player',
          onPressed: onClose,
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
              // Artwork: large, rounded, gradient glow behind it.
              AspectRatio(
                aspectRatio: 1,
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: gradient,
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius:
                        BorderRadius.circular(MpxRadius.artwork),
                    boxShadow: <BoxShadow>[
                      BoxShadow(
                        color: theme.cardShadow,
                        blurRadius: 32,
                        offset: const Offset(0, 12),
                      ),
                    ],
                  ),
                  child: Icon(
                    isAudio
                        ? Icons.music_note_rounded
                        : Icons.videocam_rounded,
                    color: Colors.white,
                    size: 96,
                  ),
                ),
              ),
              const SizedBox(height: MpxSpacing.xxl),
              Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: MpxSpacing.sm),
              Text(
                subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 14,
                  color: theme.mutedText,
                ),
              ),
              const SizedBox(height: MpxSpacing.xxl),
              // Progress: visual only, value and seek are props.
              Slider(
                value: progress.clamp(0.0, 1.0),
                onChanged: onSeek,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: <Widget>[
                  Text(
                    positionLabel,
                    style: TextStyle(
                      fontSize: 12,
                      color: theme.mutedText,
                    ),
                  ),
                  Text(
                    durationLabel,
                    style: TextStyle(
                      fontSize: 12,
                      color: theme.mutedText,
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
                    tooltip: 'Shuffle',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: () {},
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_previous_rounded),
                    tooltip: 'Previous',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: onPrevious,
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
                        isPlaying
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                        color: Colors.white,
                        size: 36,
                      ),
                      tooltip: isPlaying ? 'Pause' : 'Play',
                      onPressed: onTogglePlay,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_next_rounded),
                    tooltip: 'Next',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: onNext,
                  ),
                  IconButton(
                    icon: const Icon(Icons.repeat_rounded),
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
              if (upNextTitles.isNotEmpty) ...<Widget>[
                Text(
                  'UP NEXT',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: theme.mutedText,
                  ),
                ),
                const SizedBox(height: MpxSpacing.sm),
                for (final String upNext in upNextTitles)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainerHighest,
                        borderRadius:
                            BorderRadius.circular(MpxRadius.md),
                      ),
                      child: Icon(
                        Icons.music_note_rounded,
                        color: theme.mutedText,
                      ),
                    ),
                    title: Text(
                      upNext,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
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
