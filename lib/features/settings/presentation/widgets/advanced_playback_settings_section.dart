import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

import '../../controllers/app_settings_controller.dart';
import '../../services/app_settings_service.dart';
import '../helpers/settings_helpers.dart';
import 'common_widgets.dart';
import 'form_rows.dart';

class AdvancedPlaybackSettingsSection extends StatelessWidget {
  final AppSettingsController settings;
  final bool compact;
  final String? footerText;
  final Future<void> Function(bool value)? onAdvancedOptionsChanged;
  final Future<void> Function(VideoPerformancePreset preset)?
      onPerformancePresetChanged;
  final Future<void> Function(bool value)? onKeepScreenAwakeChanged;

  const AdvancedPlaybackSettingsSection({
    super.key,
    required this.settings,
    this.compact = false,
    this.footerText,
    this.onAdvancedOptionsChanged,
    this.onPerformancePresetChanged,
    this.onKeepScreenAwakeChanged,
  });

  Future<void> _setAdvancedOptions(bool value) async {
    await settings.setAdvancedOptionsEnabled(value);
    await onAdvancedOptionsChanged?.call(value);
  }

  Future<void> _setPerformancePreset(VideoPerformancePreset preset) async {
    await settings.setVideoPerformancePreset(preset);
    await onPerformancePresetChanged?.call(preset);
  }

  Future<void> _setKeepScreenAwake(bool value) async {
    await settings.setKeepScreenAwake(value);
    await onKeepScreenAwakeChanged?.call(value);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SettingsSwitchRow(
          icon: Icons.tune_rounded,
          title: 'Enable advanced options',
          subtitle: 'Unlock engine profiles and deeper playback behavior',
          value: settings.advancedOptionsEnabled,
          onChanged: _setAdvancedOptions,
        ),
        AnimatedCrossFade(
          duration: const Duration(milliseconds: 180),
          crossFadeState: settings.advancedOptionsEnabled
              ? CrossFadeState.showSecond
              : CrossFadeState.showFirst,
          firstChild: Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(
              'Advanced options stay hidden until you turn them on.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.mutedText,
              ),
            ),
          ),
          secondChild: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 14),
              const _SubsectionLabel(
                title: 'Playback engine profile',
                subtitle:
                    'Choose how MPx prioritizes speed, sharpness, smoothness, or compatibility.',
              ),
              const SizedBox(height: 12),
              if (compact)
                Column(
                  children: VideoPerformancePreset.values
                      .map(
                        (preset) => _PerformancePresetTile(
                          preset: preset,
                          isSelected: settings.videoPerformancePreset == preset,
                          onTap: () => _setPerformancePreset(preset),
                        ),
                      )
                      .toList(),
                )
              else
                Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: VideoPerformancePreset.values
                      .map(
                        (preset) => SizedBox(
                          width: 170,
                          child: SettingsChoiceCard(
                            icon:
                                SettingsVideoPerformanceHelpers.getIcon(preset),
                            title: SettingsVideoPerformanceHelpers.getLabel(
                                preset),
                            subtitle:
                                SettingsVideoPerformanceHelpers.getDescription(
                                    preset),
                            isSelected:
                                settings.videoPerformancePreset == preset,
                            onTap: () => _setPerformancePreset(preset),
                          ),
                        ),
                      )
                      .toList(),
                ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: const [
                  _HintChip(label: '1080p', value: 'Instant Seek / Quality'),
                  _HintChip(label: 'Low-end', value: 'Power Saver'),
                  _HintChip(label: 'Online', value: 'Streaming'),
                ],
              ),
              const SizedBox(height: 14),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary.withValues(
                    alpha: theme.isDarkMode ? 0.14 : 0.1,
                  ),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: theme.colorScheme.primary.withValues(
                      alpha: theme.isDarkMode ? 0.18 : 0.12,
                    ),
                  ),
                ),
                child: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    SettingsInlineStat(
                      label: 'Profile',
                      value: SettingsVideoPerformanceHelpers.getBadge(
                        settings.videoPerformancePreset,
                      ),
                    ),
                    SettingsInlineStat(
                      label: 'Decoder',
                      value: _decoderLabel(settings.videoPerformancePreset),
                    ),
                    SettingsInlineStat(
                      label: 'Sync',
                      value: _syncLabel(settings.videoPerformancePreset),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              const _SubsectionLabel(
                title: 'Behavior',
                subtitle:
                    'These controls affect resume flow, gestures, and screen behavior.',
              ),
              const SizedBox(height: 4),
              SettingsSwitchRow(
                icon: Icons.history_toggle_off,
                title: 'Auto resume playback',
                subtitle:
                    'Jump back to the saved position when you reopen a video',
                value: settings.autoResumePlayback,
                onChanged: settings.setAutoResumePlayback,
              ),
              SettingsSwitchRow(
                icon: Icons.screen_lock_portrait_outlined,
                title: 'Keep screen awake',
                subtitle:
                    'Prevent the display from sleeping while a video is open',
                value: settings.keepScreenAwake,
                onChanged: _setKeepScreenAwake,
              ),
              SettingsSwitchRow(
                icon: Icons.swipe_vertical_outlined,
                title: 'Swipe brightness and volume',
                subtitle:
                    'Use left and right edge swipes for quick adjustments',
                value: settings.swipeGestures,
                onChanged: settings.setSwipeGestures,
              ),
              SettingsSwitchRow(
                icon: Icons.speed,
                title: 'Hold for 2x speed',
                subtitle:
                    'Temporarily boost playback while pressing on the video',
                value: settings.holdToBoost,
                onChanged: settings.setHoldToBoost,
              ),
              if (footerText != null) ...[
                const SizedBox(height: 10),
                Text(
                  footerText!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.mutedText,
                    height: 1.4,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  String _decoderLabel(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
      case VideoPerformancePreset.balanced:
      case VideoPerformancePreset.instantSeeking:
      case VideoPerformancePreset.streaming:
        return 'HW Auto';
      case VideoPerformancePreset.quality:
      case VideoPerformancePreset.smoothMotion:
        return 'HW Copy';
      case VideoPerformancePreset.softwareDecoding:
        return 'Software';
    }
  }

  String _syncLabel(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.quality:
      case VideoPerformancePreset.smoothMotion:
        return 'Display';
      default:
        return 'Audio';
    }
  }
}

class _HintChip extends StatelessWidget {
  final String label;
  final String value;

  const _HintChip({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: theme.softBorder),
      ),
      child: RichText(
        text: TextSpan(
          style: theme.textTheme.bodySmall?.copyWith(color: theme.mutedText),
          children: [
            TextSpan(
              text: '$label: ',
              style: TextStyle(
                color: theme.strongText,
                fontWeight: FontWeight.w700,
              ),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}

class _SubsectionLabel extends StatelessWidget {
  final String title;
  final String subtitle;

  const _SubsectionLabel({required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w800,
            color: theme.strongText,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          subtitle,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.mutedText,
            height: 1.4,
          ),
        ),
      ],
    );
  }
}

class _PerformancePresetTile extends StatelessWidget {
  final VideoPerformancePreset preset;
  final bool isSelected;
  final VoidCallback onTap;

  const _PerformancePresetTile({
    required this.preset,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: isSelected
            ? theme.colorScheme.primary.withValues(alpha: 0.14)
            : theme.subtleSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isSelected
              ? theme.colorScheme.primary.withValues(alpha: 0.35)
              : theme.softBorder,
        ),
      ),
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
        leading: Icon(
          SettingsVideoPerformanceHelpers.getIcon(preset),
          color: isSelected ? theme.colorScheme.primary : theme.strongText,
        ),
        title: Text(
          SettingsVideoPerformanceHelpers.getLabel(preset),
          style: TextStyle(
            color: theme.strongText,
            fontWeight: FontWeight.w700,
          ),
        ),
        subtitle: Text(
          SettingsVideoPerformanceHelpers.getDescription(preset),
          style: TextStyle(
            color: theme.mutedText,
            fontSize: 12,
            height: 1.35,
          ),
        ),
        trailing: SettingsInlineStat(
          label: 'Mode',
          value: SettingsVideoPerformanceHelpers.getBadge(preset),
        ),
      ),
    );
  }
}
