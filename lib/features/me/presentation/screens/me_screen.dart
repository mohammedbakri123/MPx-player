import 'package:flutter/material.dart';

import '../../../../core/theme/mpx_ui_tokens.dart';

/// Me page, MX structure in MPx's own code.
///
/// A tools grid card, then plain rows. Only two rows are
/// wired today: App Theme (cycles the theme) and Settings
/// (opens the Settings page). The other tools and rows are
/// labelled "Soon" — their features do not exist yet, so
/// they are not presented as working.
class MeScreen extends StatelessWidget {
  const MeScreen({
    super.key,
    required this.themeChoice,
    required this.onThemeTap,
    required this.onSettingsTap,
    required this.onMusicTap,
    required this.onAboutTap,
  });

  final String themeChoice;
  final VoidCallback onThemeTap;
  final VoidCallback onSettingsTap;
  final VoidCallback onMusicTap;
  final VoidCallback onAboutTap;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final List<_Tool> tools = <_Tool>[
      _Tool(Icons.swap_horiz_rounded, 'File Transfer', null),
      _Tool(Icons.folder_special_rounded, 'Private Folder', null),
      _Tool(Icons.video_library_rounded, 'Video Playlists', null),
      _Tool(Icons.folder_open_rounded, 'Media Manager', null),
      _Tool(Icons.computer_rounded, 'Local Network', null),
      _Tool(Icons.music_note_rounded, 'Music Player', onMusicTap),
      _Tool(Icons.cloud_outlined, 'Cloud Drive', null),
      _Tool(Icons.delete_outline_rounded, 'Recycle Bin', null),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Me')),
      body: ListView(
        padding: const EdgeInsets.all(MpxSpacing.lg),
        children: <Widget>[
          Container(
            padding: const EdgeInsets.all(MpxSpacing.lg),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest.withValues(alpha: 0.4),
              borderRadius: BorderRadius.circular(MpxRadius.xl),
            ),
            child: GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                mainAxisSpacing: 16,
                crossAxisSpacing: 8,
                childAspectRatio: 1.1,
              ),
              itemCount: tools.length,
              itemBuilder: (BuildContext ctx, int index) {
                final _Tool tool = tools[index];
                final bool wired = tool.onTap != null;
                return InkWell(
                  onTap: tool.onTap,
                  borderRadius: BorderRadius.circular(MpxRadius.lg),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      Icon(
                        tool.icon,
                        color: wired ? scheme.primary : scheme.outline,
                        size: 32,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        tool.label,
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 13,
                          color: wired
                              ? null
                              : Theme.of(context).disabledColor,
                        ),
                      ),
                      if (!wired)
                        const Text(
                          'Soon',
                          style: TextStyle(fontSize: 11),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: MpxSpacing.lg),
          _RowCard(
            children: <Widget>[
              const ListTile(
                leading: Icon(Icons.download_rounded, color: Colors.green),
                title: Text('Status Saver'),
                trailing: Icon(Icons.chevron_right_rounded),
                enabled: false,
              ),
            ],
          ),
          const SizedBox(height: MpxSpacing.lg),
          _RowCard(
            children: <Widget>[
              ListTile(
                leading: const Icon(Icons.style_outlined),
                title: const Text('App Theme'),
                subtitle: Text(themeChoice),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: onThemeTap,
              ),
              ListTile(
                leading: const Icon(Icons.settings_outlined),
                title: const Text('Settings'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: onSettingsTap,
              ),
              const ListTile(
                leading: Icon(Icons.picture_in_picture_alt_outlined),
                title: Text('Custom Pop-up Play'),
                trailing: Icon(Icons.chevron_right_rounded),
                enabled: false,
              ),
            ],
          ),
          const SizedBox(height: MpxSpacing.lg),
          _RowCard(
            children: <Widget>[
              ListTile(
                leading: const Icon(Icons.balance_outlined),
                title: const Text('Legal'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: onAboutTap,
              ),
              const ListTile(
                leading: Icon(Icons.help_outline_rounded),
                title: Text('Help'),
                trailing: Icon(Icons.chevron_right_rounded),
                enabled: false,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Tool {
  const _Tool(this.icon, this.label, this.onTap);

  final IconData icon;
  final String label;
  final VoidCallback? onTap;
}

class _RowCard extends StatelessWidget {
  const _RowCard({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context)
            .colorScheme
            .surfaceContainerHighest
            .withValues(alpha: 0.4),
        borderRadius: BorderRadius.circular(MpxRadius.xl),
      ),
      child: Column(children: children),
    );
  }
}
