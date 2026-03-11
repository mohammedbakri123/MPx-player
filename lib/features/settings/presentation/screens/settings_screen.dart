import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../presentation/controllers/app_settings_controller.dart';
import '../../services/app_settings_service.dart';
import '../../services/subtitle_settings_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late bool _subtitleEnabled;
  late double _subtitleFontSize;
  late Color _subtitleColor;
  late bool _subtitleHasBackground;
  late FontWeight _subtitleFontWeight;
  late double _subtitleBottomPadding;
  late double _subtitleBackgroundOpacity;

  @override
  void initState() {
    super.initState();
    _subtitleEnabled = SubtitleSettingsService.isEnabled;
    _subtitleFontSize = SubtitleSettingsService.fontSize;
    _subtitleColor = SubtitleSettingsService.color;
    _subtitleHasBackground = SubtitleSettingsService.hasBackground;
    _subtitleFontWeight = SubtitleSettingsService.fontWeight;
    _subtitleBottomPadding = SubtitleSettingsService.bottomPadding;
    _subtitleBackgroundOpacity = SubtitleSettingsService.backgroundOpacity;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final settings = context.watch<AppSettingsController>();
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: colors.surface,
      body: SafeArea(
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                colors.surface,
                isDark ? const Color(0xFF0B1422) : const Color(0xFFEAEDE3),
              ],
            ),
          ),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(theme, settings),
                const SizedBox(height: 24),
                _SettingsCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const _SectionTitle(
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
                              (preference) => _ChoiceCard(
                                icon: _themeIcon(preference),
                                title: _themeLabel(preference),
                                subtitle: _themeDescription(preference),
                                isSelected:
                                    settings.themePreference == preference,
                                onTap: () => settings.setThemePreference(
                                  preference,
                                ),
                              ),
                            )
                            .toList(),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SettingsCard(
                  accent: colors.secondary,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const _SectionTitle(
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
                              (preset) => _ChoiceCard(
                                icon: _presetIcon(preset),
                                title: _presetLabel(preset),
                                subtitle: _presetDescription(preset),
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
                          color: colors.primary.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Wrap(
                          spacing: 12,
                          runSpacing: 12,
                          children: [
                            _InlineStat(
                              label: 'Speed',
                              value: _presetSpeed(settings.playerPreset),
                            ),
                            _InlineStat(
                              label: 'Aspect',
                              value: _presetAspect(settings.playerPreset),
                            ),
                            _InlineStat(
                              label: 'Repeat',
                              value: _presetRepeat(settings.playerPreset),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SettingsCard(
                  accent: const Color(0xFFEA580C),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const _SectionTitle(
                        eyebrow: 'Subtitle Settings',
                        title: 'Readable by default',
                        description:
                            'These preferences are applied to the player automatically.',
                      ),
                      const SizedBox(height: 10),
                      _SwitchRow(
                        icon: Icons.subtitles_outlined,
                        title: 'Enable subtitles',
                        subtitle: 'Keep captions on when tracks are available',
                        value: _subtitleEnabled,
                        onChanged: (value) async {
                          await SubtitleSettingsService.setEnabled(value);
                          setState(() => _subtitleEnabled = value);
                        },
                      ),
                      AnimatedCrossFade(
                        duration: const Duration(milliseconds: 180),
                        crossFadeState: _subtitleEnabled
                            ? CrossFadeState.showSecond
                            : CrossFadeState.showFirst,
                        firstChild: const SizedBox.shrink(),
                        secondChild: Column(
                          children: [
                            _SliderRow(
                              icon: Icons.format_size,
                              title: 'Font size',
                              value: '${_subtitleFontSize.round()} pt',
                              sliderValue: _subtitleFontSize,
                              min: 16,
                              max: 40,
                              onChanged: (value) async {
                                await SubtitleSettingsService.setFontSize(
                                    value);
                                setState(() => _subtitleFontSize = value);
                              },
                            ),
                            _InfoRow(
                              icon: Icons.format_bold,
                              title: 'Font weight',
                              child: Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  FontWeight.w400,
                                  FontWeight.w500,
                                  FontWeight.w600,
                                  FontWeight.w700,
                                ]
                                    .map(
                                      (weight) => ChoiceChip(
                                        label: Text(_fontWeightLabel(weight)),
                                        selected: _subtitleFontWeight == weight,
                                        onSelected: (_) async {
                                          await SubtitleSettingsService
                                              .setFontWeight(weight);
                                          setState(
                                            () => _subtitleFontWeight = weight,
                                          );
                                        },
                                      ),
                                    )
                                    .toList(),
                              ),
                            ),
                            _InfoRow(
                              icon: Icons.palette_outlined,
                              title: 'Text color',
                              child: Wrap(
                                spacing: 10,
                                runSpacing: 10,
                                children: SubtitleSettingsService
                                        .getDefaultColorOptions()
                                    .map(
                                      (color) => _ColorOption(
                                        color: color,
                                        isSelected: _subtitleColor.toARGB32() ==
                                            color.toARGB32(),
                                        onTap: () async {
                                          await SubtitleSettingsService
                                              .setColor(color);
                                          setState(
                                            () => _subtitleColor = color,
                                          );
                                        },
                                      ),
                                    )
                                    .toList(),
                              ),
                            ),
                            _SwitchRow(
                              icon: Icons.rectangle_outlined,
                              title: 'Background plate',
                              subtitle: 'Add contrast behind subtitle text',
                              value: _subtitleHasBackground,
                              onChanged: (value) async {
                                await SubtitleSettingsService.setHasBackground(
                                  value,
                                );
                                setState(() => _subtitleHasBackground = value);
                              },
                            ),
                            if (_subtitleHasBackground)
                              _SliderRow(
                                icon: Icons.opacity,
                                title: 'Background opacity',
                                value:
                                    '${(_subtitleBackgroundOpacity * 100).round()}%',
                                sliderValue: _subtitleBackgroundOpacity,
                                min: 0.2,
                                max: 1.0,
                                onChanged: (value) async {
                                  await SubtitleSettingsService
                                      .setBackgroundOpacity(value);
                                  setState(
                                    () => _subtitleBackgroundOpacity = value,
                                  );
                                },
                              ),
                            _SliderRow(
                              icon: Icons.vertical_align_bottom,
                              title: 'Bottom spacing',
                              value: '${_subtitleBottomPadding.round()} px',
                              sliderValue: _subtitleBottomPadding,
                              min: 12,
                              max: 80,
                              onChanged: (value) async {
                                await SubtitleSettingsService.setBottomPadding(
                                  value,
                                );
                                setState(() => _subtitleBottomPadding = value);
                              },
                            ),
                            const SizedBox(height: 10),
                            _SubtitlePreview(
                              color: _subtitleColor,
                              fontSize: _subtitleFontSize,
                              fontWeight: _subtitleFontWeight,
                              hasBackground: _subtitleHasBackground,
                              backgroundOpacity: _subtitleBackgroundOpacity,
                              bottomPadding: _subtitleBottomPadding,
                            ),
                            const SizedBox(height: 12),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: TextButton.icon(
                                onPressed: () async {
                                  await SubtitleSettingsService
                                      .resetToDefaults();
                                  setState(() {
                                    _subtitleEnabled =
                                        SubtitleSettingsService.isEnabled;
                                    _subtitleFontSize =
                                        SubtitleSettingsService.fontSize;
                                    _subtitleColor =
                                        SubtitleSettingsService.color;
                                    _subtitleHasBackground =
                                        SubtitleSettingsService.hasBackground;
                                    _subtitleFontWeight =
                                        SubtitleSettingsService.fontWeight;
                                    _subtitleBottomPadding =
                                        SubtitleSettingsService.bottomPadding;
                                    _subtitleBackgroundOpacity =
                                        SubtitleSettingsService
                                            .backgroundOpacity;
                                  });
                                },
                                icon: const Icon(Icons.restart_alt),
                                label: const Text('Reset subtitles'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SettingsCard(
                  accent: const Color(0xFF7C3AED),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const _SectionTitle(
                        eyebrow: 'Advanced',
                        title: 'Power-user controls',
                        description:
                            'Keep this off for a simpler app, or enable deeper playback behavior.',
                      ),
                      const SizedBox(height: 10),
                      _SwitchRow(
                        icon: Icons.tune_rounded,
                        title: 'Enable advanced options',
                        subtitle: 'Reveal extra playback and gesture controls',
                        value: settings.advancedOptionsEnabled,
                        onChanged: settings.setAdvancedOptionsEnabled,
                      ),
                      AnimatedCrossFade(
                        duration: const Duration(milliseconds: 180),
                        crossFadeState: settings.advancedOptionsEnabled
                            ? CrossFadeState.showSecond
                            : CrossFadeState.showFirst,
                        firstChild: const Padding(
                          padding: EdgeInsets.only(top: 6),
                          child: Text(
                            'Advanced options stay hidden until you turn them on.',
                          ),
                        ),
                        secondChild: Column(
                          children: [
                            _SwitchRow(
                              icon: Icons.history_toggle_off,
                              title: 'Auto resume playback',
                              subtitle:
                                  'Jump back to the saved position when you reopen a video',
                              value: settings.autoResumePlayback,
                              onChanged: settings.setAutoResumePlayback,
                            ),
                            _SwitchRow(
                              icon: Icons.screen_lock_portrait_outlined,
                              title: 'Keep screen awake',
                              subtitle:
                                  'Prevent the display from sleeping while a video is open',
                              value: settings.keepScreenAwake,
                              onChanged: settings.setKeepScreenAwake,
                            ),
                            _SwitchRow(
                              icon: Icons.swipe_vertical_outlined,
                              title: 'Swipe brightness and volume',
                              subtitle:
                                  'Use left and right edge swipes for quick adjustments',
                              value: settings.swipeGestures,
                              onChanged: settings.setSwipeGestures,
                            ),
                            _SwitchRow(
                              icon: Icons.speed,
                              title: 'Hold for 2x speed',
                              subtitle:
                                  'Temporarily boost playback while pressing on the video',
                              value: settings.holdToBoost,
                              onChanged: settings.setHoldToBoost,
                            ),
                          ],
                        ),
                      ),
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

  Widget _buildHeader(ThemeData theme, AppSettingsController settings) {
    final colors = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? const [Color(0xFF12344A), Color(0xFF0D1728)]
              : const [Color(0xFFE7F7F4), Color(0xFFF3EEDF)],
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.16 : 0.06),
            blurRadius: 26,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: colors.primary.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Icon(Icons.tune_rounded, color: colors.primary, size: 28),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Settings',
                  style: theme.textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                    letterSpacing: -1.2,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Shape the app around how you watch - theme, presets, subtitles, and deeper player behavior.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: colors.onSurface.withValues(alpha: 0.74),
                    height: 1.45,
                  ),
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _HeaderChip(label: _themeLabel(settings.themePreference)),
                    _HeaderChip(label: _presetLabel(settings.playerPreset)),
                    _HeaderChip(
                      label: settings.advancedOptionsEnabled
                          ? 'Advanced on'
                          : 'Advanced off',
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  IconData _themeIcon(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return Icons.brightness_auto_rounded;
      case AppThemePreference.light:
        return Icons.light_mode_rounded;
      case AppThemePreference.dark:
        return Icons.dark_mode_rounded;
    }
  }

  String _themeLabel(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return 'Match device';
      case AppThemePreference.light:
        return 'Light';
      case AppThemePreference.dark:
        return 'Dark';
    }
  }

  String _themeDescription(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return 'Follow your phone settings';
      case AppThemePreference.light:
        return 'Clean daylight interface';
      case AppThemePreference.dark:
        return 'Low-glare night watching';
    }
  }

  IconData _presetIcon(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return Icons.dashboard_customize_outlined;
      case PlayerPreset.cinema:
        return Icons.movie_creation_outlined;
      case PlayerPreset.binge:
        return Icons.local_fire_department_outlined;
    }
  }

  String _presetLabel(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Balanced';
      case PlayerPreset.cinema:
        return 'Cinema';
      case PlayerPreset.binge:
        return 'Binge';
    }
  }

  String _presetDescription(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Standard framing and a neutral watch setup';
      case PlayerPreset.cinema:
        return 'Immersive framing with repeat-one defaults';
      case PlayerPreset.binge:
        return 'Slightly faster playback and queue-friendly repeat';
    }
  }

  String _presetSpeed(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return '1.0x';
      case PlayerPreset.cinema:
        return '1.0x';
      case PlayerPreset.binge:
        return '1.15x';
    }
  }

  String _presetAspect(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Fit';
      case PlayerPreset.cinema:
        return 'Fill';
      case PlayerPreset.binge:
        return 'Fit';
    }
  }

  String _presetRepeat(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Off';
      case PlayerPreset.cinema:
        return 'One';
      case PlayerPreset.binge:
        return 'All';
    }
  }

  String _fontWeightLabel(FontWeight weight) {
    if (weight == FontWeight.w400) return 'Regular';
    if (weight == FontWeight.w600) return 'Semi-bold';
    if (weight == FontWeight.w700) return 'Bold';
    return 'Medium';
  }
}

class _SettingsCard extends StatelessWidget {
  final Widget child;
  final Color? accent;

  const _SettingsCard({required this.child, this.accent});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.cardTheme.color,
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: (accent ?? colors.onSurface).withValues(alpha: 0.08),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(
              alpha: theme.brightness == Brightness.dark ? 0.12 : 0.05,
            ),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: child,
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String eyebrow;
  final String title;
  final String description;

  const _SectionTitle({
    required this.eyebrow,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          eyebrow.toUpperCase(),
          style: theme.textTheme.labelMedium?.copyWith(
            color: colors.primary,
            fontWeight: FontWeight.w800,
            letterSpacing: 1.4,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          title,
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          description,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: colors.onSurface.withValues(alpha: 0.72),
            height: 1.45,
          ),
        ),
      ],
    );
  }
}

class _ChoiceCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool isSelected;
  final VoidCallback onTap;

  const _ChoiceCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return SizedBox(
      width: 170,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Ink(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            color: isSelected
                ? colors.primary.withValues(alpha: 0.12)
                : colors.onSurface.withValues(alpha: 0.03),
            border: Border.all(
              color: isSelected
                  ? colors.primary.withValues(alpha: 0.35)
                  : colors.onSurface.withValues(alpha: 0.08),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: isSelected ? colors.primary : colors.onSurface),
              const SizedBox(height: 14),
              Text(
                title,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: colors.onSurface.withValues(alpha: 0.68),
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InlineStat extends StatelessWidget {
  final String label;
  final String value;

  const _InlineStat({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: theme.cardTheme.color,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: theme.textTheme.bodySmall?.copyWith(
              color: colors.onSurface.withValues(alpha: 0.6),
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            value,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _SwitchRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _SwitchRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: colors.onSurface.withValues(alpha: 0.03),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: colors.primary.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: colors.primary, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: colors.onSurface.withValues(alpha: 0.68),
                      height: 1.35,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Switch.adaptive(value: value, onChanged: onChanged),
          ],
        ),
      ),
    );
  }
}

class _SliderRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String value;
  final double sliderValue;
  final double min;
  final double max;
  final ValueChanged<double> onChanged;

  const _SliderRow({
    required this.icon,
    required this.title,
    required this.value,
    required this.sliderValue,
    required this.min,
    required this.max,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Container(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
        decoration: BoxDecoration(
          color: colors.onSurface.withValues(alpha: 0.03),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          children: [
            Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    color: colors.secondary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(icon, color: colors.secondary, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                Text(
                  value,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.onSurface.withValues(alpha: 0.7),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
            Slider(
                value: sliderValue, min: min, max: max, onChanged: onChanged),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final Widget child;

  const _InfoRow({
    required this.icon,
    required this.title,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: colors.onSurface.withValues(alpha: 0.03),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    color: colors.primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(icon, color: colors.primary, size: 20),
                ),
                const SizedBox(width: 12),
                Text(
                  title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            child,
          ],
        ),
      ),
    );
  }
}

class _ColorOption extends StatelessWidget {
  final Color color;
  final bool isSelected;
  final VoidCallback onTap;

  const _ColorOption({
    required this.color,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        width: 34,
        height: 34,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(
            color: isSelected
                ? Theme.of(context).colorScheme.primary
                : Colors.white.withValues(alpha: 0.7),
            width: isSelected ? 3 : 1,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.12),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
      ),
    );
  }
}

class _SubtitlePreview extends StatelessWidget {
  final Color color;
  final double fontSize;
  final FontWeight fontWeight;
  final bool hasBackground;
  final double backgroundOpacity;
  final double bottomPadding;

  const _SubtitlePreview({
    required this.color,
    required this.fontSize,
    required this.fontWeight,
    required this.hasBackground,
    required this.backgroundOpacity,
    required this.bottomPadding,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF111827), Color(0xFF1F2937)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Preview',
            style: TextStyle(
              color: Colors.white70,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          SizedBox(height: bottomPadding * 0.35),
          Center(
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: hasBackground
                    ? Colors.black.withValues(alpha: backgroundOpacity)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                child: Text(
                  'This is how your subtitles will look',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: color,
                    fontSize: fontSize,
                    fontWeight: fontWeight,
                    height: 1.2,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HeaderChip extends StatelessWidget {
  final String label;

  const _HeaderChip({required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: theme.colorScheme.onSurface.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: theme.textTheme.labelLarge?.copyWith(
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
