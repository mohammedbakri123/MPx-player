import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:mpx/features/downloader/presentation/screens/downloader_settings_screen.dart';
import 'package:mpx/features/downloader/presentation/screens/downloads_manager_screen.dart';
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

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final Set<int> _expandedSections = {};

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final settings = context.watch<AppSettingsController>();

    return Scaffold(
      backgroundColor: theme.appBackground,
      resizeToAvoidBottomInset: false,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SettingsHeader(),
              const SizedBox(height: 16),
              _buildExpandableSection(
                index: 0,
                title: 'Appearance',
                icon: Icons.palette_outlined,
                colors: colors,
                child: _ThemeSection(settings: settings),
              ),
              _buildExpandableSection(
                index: 1,
                title: 'Subtitles',
                icon: Icons.subtitles_outlined,
                accent: const Color(0xFFEA580C),
                colors: colors,
                child: const _SubtitleSection(),
              ),
              _buildExpandableSection(
                index: 2,
                title: 'Playback',
                icon: Icons.play_circle_outline,
                colors: colors,
                child: _PlaybackSection(settings: settings),
              ),
              _buildExpandableSection(
                index: 3,
                title: 'Engine',
                icon: Icons.tune,
                accent: const Color(0xFF0F766E),
                colors: colors,
                child: _EngineSection(settings: settings),
              ),
              _buildExpandableSection(
                index: 4,
                title: 'Downloader',
                icon: Icons.download_rounded,
                accent: const Color(0xFF2563EB),
                colors: colors,
                child: const _DownloaderSection(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildExpandableSection({
    required int index,
    required String title,
    required IconData icon,
    required ColorScheme colors,
    Color? accent,
    required Widget child,
  }) {
    final isExpanded = _expandedSections.contains(index);

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isExpanded
                ? (accent ?? colors.primary).withValues(alpha: 0.3)
                : colors.outlineVariant.withValues(alpha: 0.3),
            width: 1,
          ),
        ),
        child: Column(
          children: [
            InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: () {
                setState(() {
                  if (isExpanded) {
                    _expandedSections.remove(index);
                  } else {
                    _expandedSections.add(index);
                  }
                });
              },
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color:
                            (accent ?? colors.primary).withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        icon,
                        color: accent ?? colors.primary,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        title,
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: colors.onSurface,
                        ),
                      ),
                    ),
                    AnimatedRotation(
                      turns: isExpanded ? 0.5 : 0,
                      duration: const Duration(milliseconds: 200),
                      child: Icon(
                        Icons.keyboard_arrow_down,
                        color: colors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            AnimatedCrossFade(
              firstChild: child,
              secondChild: const SizedBox.shrink(),
              crossFadeState: isExpanded
                  ? CrossFadeState.showFirst
                  : CrossFadeState.showSecond,
              duration: const Duration(milliseconds: 200),
            ),
          ],
        ),
      ),
    );
  }
}

class _ThemeSection extends StatelessWidget {
  final AppSettingsController settings;

  const _ThemeSection({required this.settings});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Pick light, dark, or follow device.',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: AppThemePreference.values
                .map(
                  (preference) => SizedBox(
                    width: 150,
                    child: SettingsChoiceCard(
                      icon: SettingsThemeHelpers.getIcon(preference),
                      title: SettingsThemeHelpers.getLabel(preference),
                      subtitle: SettingsThemeHelpers.getDescription(preference),
                      isSelected: settings.themePreference == preference,
                      onTap: () => settings.setThemePreference(preference),
                    ),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _SubtitleSection extends StatelessWidget {
  const _SubtitleSection();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Applied to player automatically.',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 12),
          SettingsSwitchRow(
            icon: Icons.subtitles_outlined,
            title: 'Enable subtitles',
            subtitle: 'Keep captions on when available',
            value: SubtitleSettingsService.isEnabled,
            onChanged: (value) async {
              await SubtitleSettingsService.setEnabled(value);
            },
          ),
          const SubtitleSettingsSection(),
        ],
      ),
    );
  }
}

class _PlaybackSection extends StatelessWidget {
  final AppSettingsController settings;

  const _PlaybackSection({required this.settings});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Control resume, wake lock, gestures.',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 12),
          AdvancedPlaybackSettingsSection(settings: settings),
        ],
      ),
    );
  }
}

class _EngineSection extends StatelessWidget {
  final AppSettingsController settings;

  const _EngineSection({required this.settings});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Decoder, sync, scaling, cache. Expert mode.',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 12),
          ExpertEngineSettingsSection(settings: settings),
        ],
      ),
    );
  }
}

class _DownloaderSection extends StatelessWidget {
  const _DownloaderSection();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Manage on-device downloads and downloader preferences.',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 12),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.download_for_offline_outlined),
            title: const Text('Open downloader'),
            subtitle: const Text('Queue URLs and manage active downloads'),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const DownloadsManagerScreen(),
                ),
              );
            },
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.tune_rounded),
            title: const Text('Downloader settings'),
            subtitle: const Text('Quality, cookies, updates, and diagnostics'),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const DownloaderSettingsScreen(),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
