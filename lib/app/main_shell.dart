import 'package:flutter/material.dart';

import '../features/library/presentation/screens/home_screen.dart';
import '../features/me/presentation/screens/me_screen.dart';
import '../features/music/presentation/screens/music_screen.dart';
import '../features/settings/presentation/screens/settings_screen.dart';
import '../features/transfer/presentation/screens/transfer_screen.dart';

/// Bottom-navigation shell, MX structure: Local, Music,
/// Transfer, Me.
///
/// This replaces the old Library / Favourites / History /
/// Settings tabs, which did not match MX's pages. Pages are
/// kept in an [IndexedStack] so Local keeps its scroll
/// position and state when the user switches tabs.
/// The Player is NOT a tab: it opens as a full-screen
/// route from the mini player dock. Settings is NOT a tab
/// either: it opens as a route from the Me page.
class MpxMainShell extends StatefulWidget {
  const MpxMainShell({
    super.key,
    this.themeMode = ThemeMode.dark,
    this.onThemeModeChanged,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode>? onThemeModeChanged;

  @override
  State<MpxMainShell> createState() => _MpxMainShellState();
}

class _MpxMainShellState extends State<MpxMainShell> {
  int _currentIndex = 0;

  String get _themeChoice {
    switch (widget.themeMode) {
      case ThemeMode.dark:
        return 'Dark';
      case ThemeMode.light:
        return 'Light';
      case ThemeMode.system:
        return 'System';
    }
  }

  void _showAbout() {
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) {
        return AlertDialog(
          title: const Text('MPx Player'),
          content: const Text(
            'A local media player — your files never leave your device.',
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        );
      },
    );
  }

  void _cycleTheme() {
    final ThemeMode next = switch (widget.themeMode) {
      ThemeMode.system => ThemeMode.dark,
      ThemeMode.dark => ThemeMode.light,
      ThemeMode.light => ThemeMode.system,
    };
    widget.onThemeModeChanged?.call(next);
  }

  void _openSettings() {
    Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (BuildContext context) => const SettingsScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: <Widget>[
          HomeScreen(
            onMusicTap: () {
              setState(() {
                _currentIndex = 1;
              });
            },
          ),
          const MusicScreen(),
          const TransferScreen(),
          MeScreen(
            themeChoice: _themeChoice,
            onThemeTap: _cycleTheme,
            onSettingsTap: _openSettings,
            onMusicTap: () {
              setState(() {
                _currentIndex = 1;
              });
            },
            onAboutTap: _showAbout,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (int index) {
          setState(() {
            _currentIndex = index;
          });
        },
        destinations: const <NavigationDestination>[
          NavigationDestination(
            icon: Icon(Icons.folder_outlined),
            selectedIcon: Icon(Icons.folder_rounded),
            label: 'Local',
          ),
          NavigationDestination(
            icon: Icon(Icons.music_note_outlined),
            selectedIcon: Icon(Icons.music_note_rounded),
            label: 'Music',
          ),
          NavigationDestination(
            icon: Icon(Icons.swap_horiz_outlined),
            selectedIcon: Icon(Icons.swap_horiz_rounded),
            label: 'Transfer',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outlined),
            selectedIcon: Icon(Icons.person_rounded),
            label: 'Me',
          ),
        ],
      ),
    );
  }
}
