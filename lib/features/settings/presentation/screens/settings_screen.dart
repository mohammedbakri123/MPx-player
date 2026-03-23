import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:provider/provider.dart';

import '../../controllers/app_settings_controller.dart';
import '../../services/app_settings_service.dart';
import '../../services/subtitle_settings_service.dart';
import '../helpers/settings_helpers.dart';
import '../widgets/settings_header.dart';
import '../widgets/common_widgets.dart';
import '../widgets/form_rows.dart';
import '../widgets/advanced_playback_settings_section.dart';
import '../widgets/expert_engine_settings_section.dart';
import '../widgets/subtitle_settings_section.dart';

/// Main settings screen with theme, presets, subtitle, and advanced options
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final settings = context.watch<AppSettingsController>();

    return Scaffold(
      backgroundColor: theme.appBackground,
      body: SafeArea(
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                theme.appBackground,
                theme.appBackgroundAlt,
              ],
            ),
          ),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SettingsHeader(settings: settings),
                const SizedBox(height: 24),
                SettingsCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SettingsSectionTitle(
                        eyebrow: 'Appearance',
                        title: 'Theme that actually sticks',
                        description:
                            'Pick light, dark, or let MPx follow the device.',
                      ),
                      const SizedBox(height: 18),
                      Wrap(
                        spacing: 12,
                        runSpacing: 12,
                        children: AppThemePreference.values
                            .map(
                              (preference) => SettingsChoiceCard(
                                icon: SettingsThemeHelpers.getIcon(preference),
                                title:
                                    SettingsThemeHelpers.getLabel(preference),
                                subtitle: SettingsThemeHelpers.getDescription(
                                    preference),
                                isSelected:
                                    settings.themePreference == preference,
                                onTap: () =>
                                    settings.setThemePreference(preference),
                              ),
                            )
                            .toList(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SettingsCard(
                  accent: colors.secondary,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SettingsSectionTitle(
                        eyebrow: 'Player Presets',
                        title: 'Choose your playback vibe',
                        description:
                            'Each preset changes the default speed, framing, and repeat behavior for new videos.',
                      ),
                      const SizedBox(height: 18),
                      Wrap(
                        spacing: 12,
                        runSpacing: 12,
                        children: PlayerPreset.values
                            .map(
                              (preset) => SettingsChoiceCard(
                                icon: SettingsPresetHelpers.getIcon(preset),
                                title: SettingsPresetHelpers.getLabel(preset),
                                subtitle: SettingsPresetHelpers.getDescription(
                                    preset),
                                isSelected: settings.playerPreset == preset,
                                onTap: () => settings.setPlayerPreset(preset),
                              ),
                            )
                            .toList(),
                      ),
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(
                            alpha: theme.isDarkMode ? 0.14 : 0.1,
                          ),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: colors.primary.withValues(
                              alpha: theme.isDarkMode ? 0.18 : 0.12,
                            ),
                          ),
                        ),
                        child: Wrap(
                          spacing: 12,
                          runSpacing: 12,
                          children: [
                            SettingsInlineStat(
                              label: 'Speed',
                              value: SettingsPresetHelpers.getSpeed(
                                  settings.playerPreset),
                            ),
                            SettingsInlineStat(
                              label: 'Aspect',
                              value: SettingsPresetHelpers.getAspect(
                                  settings.playerPreset),
                            ),
                            SettingsInlineStat(
                              label: 'Repeat',
                              value: SettingsPresetHelpers.getRepeat(
                                  settings.playerPreset),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SettingsCard(
                  accent: const Color(0xFFEA580C),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SettingsSectionTitle(
                        eyebrow: 'Subtitle Settings',
                        title: 'Readable by default',
                        description:
                            'These preferences are applied to the player automatically.',
                      ),
                      const SizedBox(height: 10),
                      SettingsSwitchRow(
                        icon: Icons.subtitles_outlined,
                        title: 'Enable subtitles',
                        subtitle: 'Keep captions on when tracks are available',
                        value: SubtitleSettingsService.isEnabled,
                        onChanged: (value) async {
                          await SubtitleSettingsService.setEnabled(value);
                          setState(() {});
                        },
                      ),
                      const SubtitleSettingsSection(),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SettingsCard(
                  accent: colors.primary,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SettingsSectionTitle(
                        eyebrow: 'Player Behavior',
                        title: 'How the player should behave',
                        description:
                            'Control resume, wake lock, and gesture behavior without touching engine internals.',
                      ),
                      const SizedBox(height: 10),
                      AdvancedPlaybackSettingsSection(settings: settings),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SettingsCard(
                  accent: const Color(0xFF0F766E),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SettingsSectionTitle(
                        eyebrow: 'Expert Engine',
                        title: 'Real mpv tuning',
                        description:
                            'Change decoder, sync, scaling, cache, and seek internals. Expert mode overrides the simple engine profile.',
                      ),
                      const SizedBox(height: 10),
                      ExpertEngineSettingsSection(settings: settings),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
