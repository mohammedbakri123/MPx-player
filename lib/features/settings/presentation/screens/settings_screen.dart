import 'package:flutter/material.dart';

import '../../../../core/ui/mpx_section_header.dart';

/// Settings page. The Theme row is wired: tapping it cycles
/// System -> Dark -> Light, and the choice takes effect
/// immediately (persistence belongs to a settings service
/// later). The "Coming soon" playback rows are honest
/// placeholders, not fake controls.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({
    super.key,
    this.themeChoice = 'System',
    required this.onThemeTap,
    required this.onAboutTap,
  });

  final String themeChoice;
  final VoidCallback onThemeTap;
  final VoidCallback onAboutTap;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: <Widget>[
          const MpxSectionHeader(title: 'APPEARANCE', count: 1),
          ListTile(
            leading: const Icon(Icons.dark_mode_outlined),
            title: const Text('Theme'),
            subtitle: Text(themeChoice),
            onTap: onThemeTap,
          ),
          const MpxSectionHeader(title: 'LIBRARY', count: 1),
          const ListTile(
            leading: Icon(Icons.folder_outlined),
            title: Text('Default view'),
            subtitle: Text('Grid'),
          ),
          const MpxSectionHeader(title: 'PLAYBACK', count: 3),
          const ListTile(
            leading: Icon(Icons.speed_rounded),
            title: Text('Playback speed'),
            subtitle: Text('Coming soon'),
            enabled: false,
          ),
          const ListTile(
            leading: Icon(Icons.subtitles_outlined),
            title: Text('Subtitles'),
            subtitle: Text('Coming soon'),
            enabled: false,
          ),
          const ListTile(
            leading: Icon(Icons.memory_rounded),
            title: Text('Decoder'),
            subtitle: Text('Coming soon'),
            enabled: false,
          ),
          const MpxSectionHeader(title: 'ABOUT', count: 1),
          ListTile(
            leading: const Icon(Icons.info_outline_rounded),
            title: const Text('About MPx'),
            subtitle: const Text(
              'A local player — your files never leave your device.',
            ),
            onTap: onAboutTap,
          ),
        ],
      ),
    );
  }
}
