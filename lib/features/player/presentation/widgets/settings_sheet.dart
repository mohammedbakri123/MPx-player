import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import '../../controller/player_controller.dart';
import 'helpers/bottom_sheet_handle.dart';

class SettingsSheet extends StatelessWidget {
  final PlayerController controller;
  final VoidCallback? onOpenSubtitleSettings;

  const SettingsSheet({
    super.key,
    required this.controller,
    this.onOpenSubtitleSettings,
  });

  String _formatAspectRatio(PlayerController controller) {
    return controller.getAspectRatioLabel(controller.aspectRatioMode);
  }

  String _formatRepeatMode(PlayerController controller) {
    return controller.getRepeatModeLabel(controller.repeatMode);
  }

  String _formatAudioTrack(PlayerController controller) {
    if (controller.audioTracks.isEmpty) return 'Default';

    final safeIndex = controller.currentAudioTrackIndex < 0
        ? 0
        : controller.currentAudioTrackIndex >= controller.audioTracks.length
            ? controller.audioTracks.length - 1
            : controller.currentAudioTrackIndex;

    final track = controller.audioTracks[safeIndex];
    final title = track.title?.trim();
    final language = track.language?.trim();

    if (title != null && title.isNotEmpty) return title;
    if (language != null && language.isNotEmpty) return language.toUpperCase();
    return 'Track ${safeIndex + 1}';
  }

  String _formatSensitivity(double sensitivity) {
    if (sensitivity <= 0.15) return 'Low';
    if (sensitivity <= 0.4) return 'Medium';
    return 'High';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: AnimatedBuilder(
          animation: controller,
          builder: (context, _) {
            return SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Center(child: BottomSheetHandle()),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
                    child: Text(
                      'Player Settings',
                      style: TextStyle(
                        color: theme.strongText,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: Text(
                      'Keep the screen clean and put the extra controls here.',
                      style: TextStyle(
                        color: theme.mutedText,
                        fontSize: 12,
                      ),
                    ),
                  ),
                  _SettingsSection(
                    title: 'Playback',
                    child: Column(
                      children: [
                        _SettingsTile(
                          icon: Icons.speed,
                          title: 'Playback speed',
                          subtitle: 'Change the current speed',
                          value: '${controller.playbackSpeed}x',
                          onTap: () {
                            showModalBottomSheet(
                              context: context,
                              backgroundColor: Colors.transparent,
                              builder: (ctx) => _SettingsSpeedSheet(
                                currentSpeed: controller.playbackSpeed,
                                onSpeedSelected: controller.setSpeed,
                              ),
                            );
                          },
                        ),
                        _SettingsTile(
                          icon: Icons.aspect_ratio,
                          title: 'Aspect ratio',
                          subtitle: 'Fit, fill, stretch, and classic ratios',
                          value: _formatAspectRatio(controller),
                          onTap: controller.cycleAspectRatio,
                        ),
                        _SettingsTile(
                          icon: Icons.repeat,
                          title: 'Repeat mode',
                          subtitle: 'Choose what happens at the end',
                          value: _formatRepeatMode(controller),
                          onTap: controller.cycleRepeatMode,
                        ),
                        _SettingsTile(
                          icon: Icons.touch_app,
                          title: 'Double tap seek',
                          subtitle: 'Seconds to skip per tap',
                          value: '${controller.doubleTapSeekStep}s',
                          onTap: () {
                            showModalBottomSheet(
                              context: context,
                              backgroundColor: Colors.transparent,
                              builder: (ctx) => _SeekStepSheet(
                                currentStep: controller.doubleTapSeekStep,
                                onStepSelected: controller.setDoubleTapSeekStep,
                              ),
                            );
                          },
                        ),
                        _SettingsTile(
                          icon: Icons.swipe,
                          title: 'Drag sensitivity',
                          subtitle: 'How much seek distance covers',
                          value: _formatSensitivity(
                              controller.dragSeekSensitivity),
                          onTap: () {
                            showModalBottomSheet(
                              context: context,
                              backgroundColor: Colors.transparent,
                              builder: (ctx) => _SensitivitySheet(
                                currentSensitivity:
                                    controller.dragSeekSensitivity,
                                onSensitivitySelected:
                                    controller.setDragSeekSensitivity,
                              ),
                            );
                          },
                        ),
                        _VolumeTile(controller: controller),
                      ],
                    ),
                  ),
                  _SettingsSection(
                    title: 'Tracks & Screen',
                    child: Column(
                      children: [
                        if (onOpenSubtitleSettings != null)
                          _SettingsTile(
                            icon: Icons.subtitles_outlined,
                            title: 'Subtitle settings',
                            subtitle: controller.subtitlesEnabled
                                ? 'Customize subtitles'
                                : 'Subtitles are currently off',
                            value: controller.subtitlesEnabled ? 'On' : 'Off',
                            onTap: () {
                              Navigator.pop(context);
                              onOpenSubtitleSettings!();
                            },
                          ),
                        if (controller.audioTracks.length > 1)
                          _SettingsTile(
                            icon: Icons.audiotrack,
                            title: 'Audio track',
                            subtitle: 'Switch language or commentary track',
                            value: _formatAudioTrack(controller),
                            onTap: () {
                              showModalBottomSheet(
                                context: context,
                                backgroundColor: Colors.transparent,
                                builder: (ctx) => _AudioTrackSheet(
                                  audioTracks: controller.audioTracks,
                                  currentIndex:
                                      controller.currentAudioTrackIndex,
                                  onSelected: (index) {
                                    controller.setAudioTrack(index);
                                    Navigator.pop(ctx);
                                  },
                                  onRefresh: () {
                                    controller.loadAudioTracks();
                                    Navigator.pop(ctx);
                                  },
                                ),
                              );
                            },
                          ),
                        _SettingsTile(
                          icon: controller.isFullscreen
                              ? Icons.fullscreen_exit
                              : Icons.fullscreen,
                          title: 'Screen mode',
                          subtitle: 'Toggle portrait and landscape player',
                          value:
                              controller.isFullscreen ? 'Fullscreen' : 'Inline',
                          onTap: controller.toggleFullscreen,
                        ),
                        _SettingsTile(
                          icon: controller.isLocked
                              ? Icons.lock
                              : Icons.lock_open,
                          title: 'Lock controls',
                          subtitle: 'Prevent accidental touches while watching',
                          value: controller.isLocked ? 'Locked' : 'Unlocked',
                          onTap: () {
                            controller.toggleLock();
                            Navigator.pop(context);
                          },
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 20),
                    child: Text(
                      'Tip: tap the center of the video to show or hide controls.',
                      style: TextStyle(
                        color: theme.mutedText,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _SettingsSection extends StatelessWidget {
  final String title;
  final Widget child;

  const _SettingsSection({
    required this.title,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
      child: Container(
        decoration: BoxDecoration(
          color: theme.subtleSurface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: theme.softBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
              child: Text(
                title,
                style: TextStyle(
                  color: theme.strongText,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            child,
          ],
        ),
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String value;
  final VoidCallback onTap;

  const _SettingsTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ListTile(
      leading: Icon(icon, color: theme.strongText),
      title: Text(
        title,
        style: TextStyle(color: theme.strongText),
      ),
      subtitle: Text(
        subtitle,
        style: TextStyle(color: theme.mutedText, fontSize: 12),
      ),
      trailing: _SettingsValueChip(label: value),
      onTap: onTap,
    );
  }
}

class _VolumeTile extends StatelessWidget {
  final PlayerController controller;

  const _VolumeTile({required this.controller});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Volume',
            style: TextStyle(
              color: theme.strongText,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
          Row(
            children: [
              Icon(Icons.volume_down, color: theme.mutedText, size: 20),
              Expanded(
                child: Slider(
                  value: controller.volume,
                  min: 0,
                  max: 100,
                  activeColor: theme.colorScheme.primary,
                  inactiveColor: Colors.grey.shade700,
                  onChanged: controller.setVolume,
                ),
              ),
              Icon(Icons.volume_up, color: theme.mutedText, size: 20),
              SizedBox(
                width: 42,
                child: Text(
                  '${controller.volume.round()}%',
                  textAlign: TextAlign.right,
                  style: TextStyle(
                    color: theme.mutedText,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SettingsValueChip extends StatelessWidget {
  final String label;

  const _SettingsValueChip({required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: theme.colorScheme.primary.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: theme.colorScheme.primary,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _SettingsSpeedSheet extends StatelessWidget {
  final double currentSpeed;
  final ValueChanged<double> onSpeedSelected;

  const _SettingsSpeedSheet({
    required this.currentSpeed,
    required this.onSpeedSelected,
  });

  static const List<double> speeds = [
    0.25,
    0.5,
    0.75,
    1.0,
    1.25,
    1.5,
    1.75,
    2.0,
    2.5,
    3.0,
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Playback Speed',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${currentSpeed}x',
                    style: TextStyle(
                      color: theme.mutedText,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 300),
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: speeds.length,
                itemBuilder: (context, index) {
                  final speed = speeds[index];
                  final isSelected = speed == currentSpeed;

                  return ListTile(
                    title: Text(
                      speed == 1.0 ? 'Normal' : '${speed}x',
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () {
                      onSpeedSelected(speed);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _AudioTrackSheet extends StatefulWidget {
  final List<AudioTrackInfo> audioTracks;
  final int currentIndex;
  final ValueChanged<int> onSelected;
  final VoidCallback onRefresh;

  const _AudioTrackSheet({
    required this.audioTracks,
    required this.currentIndex,
    required this.onSelected,
    required this.onRefresh,
  });

  @override
  State<_AudioTrackSheet> createState() => _AudioTrackSheetState();
}

class _AudioTrackSheetState extends State<_AudioTrackSheet> {
  bool _refreshing = false;

  Future<void> _handleRefresh() async {
    setState(() => _refreshing = true);
    widget.onRefresh();
    await Future.delayed(const Duration(milliseconds: 500));
    if (mounted) setState(() => _refreshing = false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Audio Track',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Row(
                    children: [
                      Text(
                        '${widget.audioTracks.length} tracks',
                        style: TextStyle(
                          color: theme.mutedText,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton(
                        icon: _refreshing
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child:
                                    CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.refresh, size: 20),
                        onPressed: _refreshing ? null : _handleRefresh,
                        tooltip: 'Refresh tracks',
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: widget.audioTracks.length,
                itemBuilder: (context, index) {
                  final track = widget.audioTracks[index];
                  final isSelected = index == widget.currentIndex;

                  return ListTile(
                    title: Text(
                      track.title?.trim().isNotEmpty == true
                          ? track.title!
                          : 'Track ${index + 1}',
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                    subtitle: track.language?.trim().isNotEmpty == true
                        ? Text(
                            track.language!,
                            style: TextStyle(
                              color: theme.faintText,
                              fontSize: 12,
                            ),
                          )
                        : null,
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () => widget.onSelected(index),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _SeekStepSheet extends StatelessWidget {
  final int currentStep;
  final ValueChanged<int> onStepSelected;

  const _SeekStepSheet({
    required this.currentStep,
    required this.onStepSelected,
  });

  static const List<int> steps = [10, 15, 20, 30, 60];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Double Tap Seek Step',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${currentStep}s',
                    style: TextStyle(
                      color: theme.mutedText,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: steps.length,
                itemBuilder: (context, index) {
                  final step = steps[index];
                  final isSelected = step == currentStep;

                  return ListTile(
                    title: Text(
                      '${step}s',
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () {
                      onStepSelected(step);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _SensitivitySheet extends StatelessWidget {
  final double currentSensitivity;
  final ValueChanged<double> onSensitivitySelected;

  const _SensitivitySheet({
    required this.currentSensitivity,
    required this.onSensitivitySelected,
  });

  static const List<MapEntry<String, double>> options = [
    MapEntry('Low', 0.15),
    MapEntry('Medium', 0.3),
    MapEntry('High', 0.5),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Drag Sensitivity',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    options
                        .firstWhere(
                          (e) => e.value == currentSensitivity,
                          orElse: () => const MapEntry('Medium', 0.3),
                        )
                        .key,
                    style: TextStyle(
                      color: theme.mutedText,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: options.length,
                itemBuilder: (context, index) {
                  final option = options[index];
                  final isSelected = option.value == currentSensitivity;

                  return ListTile(
                    title: Text(
                      option.key,
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () {
                      onSensitivitySelected(option.value);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
