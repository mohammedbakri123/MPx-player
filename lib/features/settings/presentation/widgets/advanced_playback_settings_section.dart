import 'package:flutter/material.dart';

import '../../controllers/app_settings_controller.dart';
import 'form_rows.dart';

class AdvancedPlaybackSettingsSection extends StatelessWidget {
  final AppSettingsController settings;
  final Future<void> Function(bool value)? onAdvancedOptionsChanged;
  final Future<void> Function(bool value)? onKeepScreenAwakeChanged;
  final String? footerText;

  const AdvancedPlaybackSettingsSection({
    super.key,
    required this.settings,
    this.onAdvancedOptionsChanged,
    this.onKeepScreenAwakeChanged,
    this.footerText,
  });

  Future<void> _setAdvancedOptions(bool value) async {
    await settings.setAdvancedOptionsEnabled(value);
    await onAdvancedOptionsChanged?.call(value);
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
          title: 'Enable player behavior controls',
          subtitle: 'Show resume, wake-lock, and gesture behavior options',
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
              'Behavior controls stay hidden until you turn them on.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color:
                    theme.textTheme.bodyMedium?.color?.withValues(alpha: 0.7),
              ),
            ),
          ),
          secondChild: Column(
            children: [
              const SizedBox(height: 10),
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
                    color: theme.textTheme.bodySmall?.color
                        ?.withValues(alpha: 0.7),
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
}
