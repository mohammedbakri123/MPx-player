import 'package:flutter/material.dart';

import '../features/favorites/presentation/screens/favorites_screen.dart';
import '../features/history/presentation/screens/history_screen.dart';
import '../features/library/presentation/screens/home_screen.dart';
import '../features/settings/presentation/screens/settings_screen.dart';

/// Bottom-navigation shell: Library, Favourites, History, Settings.
///
/// Pages are kept in an [IndexedStack] so the Library keeps its
/// scroll position and state when the user switches tabs.
/// Favourites, History, and Settings are still UI shells: they
/// receive empty display data for now, and their real data comes
/// from controllers in a later step. The Player is NOT a tab:
/// it opens as a full-screen route from the mini player dock.
class MpxMainShell extends StatefulWidget {
  const MpxMainShell({
    super.key,
    this.themeMode = ThemeMode.system,
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

  void _cycleTheme() {
    final ThemeMode next = switch (widget.themeMode) {
      ThemeMode.system => ThemeMode.dark,
      ThemeMode.dark => ThemeMode.light,
      ThemeMode.light => ThemeMode.system,
    };
    widget.onThemeModeChanged?.call(next);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: <Widget>[
          const HomeScreen(),
          FavoritesScreen(
            onOpenTitle: (_) {},
          ),
          HistoryScreen(
            onOpenTitle: (_) {},
          ),
          SettingsScreen(
            themeChoice: _themeChoice,
            onThemeTap: _cycleTheme,
            onAboutTap: () {},
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
            label: 'Library',
          ),
          NavigationDestination(
            icon: Icon(Icons.star_border_rounded),
            selectedIcon: Icon(Icons.star_rounded),
            label: 'Favourites',
          ),
          NavigationDestination(
            icon: Icon(Icons.history_rounded),
            selectedIcon: Icon(Icons.history_rounded),
            label: 'History',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings_rounded),
            label: 'Settings',
          ),
        ],
      ),
    );
  }
}
