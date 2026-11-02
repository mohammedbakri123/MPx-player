import 'package:flutter/material.dart';

/// Settings page, MX structure in MPx's own code.
///
/// A plain black list, as in MX's Settings screenshot:
/// List, Player, Decoder, Audio, Subtitle, General,
/// Development, App Language. None of these sub-pages
/// exists in MPx yet, so every row is an honest disabled
/// "Coming soon" row — not a fake settings page.
/// Theme is changed from the Me page's App Theme row.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  static const List<({IconData icon, String label})> _rows =
      <({IconData icon, String label})>[
    (icon: Icons.list_rounded, label: 'List'),
    (icon: Icons.play_circle_outline_rounded, label: 'Player'),
    (icon: Icons.memory_rounded, label: 'Decoder'),
    (icon: Icons.music_note_outlined, label: 'Audio'),
    (icon: Icons.subtitles_outlined, label: 'Subtitle'),
    (icon: Icons.error_outline_rounded, label: 'General'),
    (icon: Icons.code_rounded, label: 'Development'),
    (icon: Icons.language_outlined, label: 'App Language'),
  ];

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView.builder(
        itemCount: _rows.length,
        itemBuilder: (BuildContext ctx, int index) {
          final row = _rows[index];
          return ListTile(
            leading: Icon(row.label == 'List' ? Icons.list_rounded : row.icon,
                color: scheme.primary),
            title: Text(row.label),
            subtitle: const Text('Coming soon'),
            enabled: false,
          );
        },
      ),
    );
  }
}
