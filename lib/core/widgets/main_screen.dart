import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:mpx/features/library/presentation/screens/home_screen.dart';
import 'package:mpx/features/favorites/presentation/screens/favorites_screen.dart';
import 'package:mpx/features/history/presentation/screens/history_screen.dart';
import 'package:mpx/features/settings/presentation/screens/settings_screen.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  static const double _swipeVelocityThreshold = 320;
  int _currentIndex = 0;
  final List<bool> _loadedTabs = [true, false, false, false];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _warmUpTabs();
    });
  }

  Future<void> _warmUpTabs() async {
    for (final index in [1, 2, 3]) {
      await Future<void>.delayed(const Duration(milliseconds: 140));
      if (!mounted || _loadedTabs[index]) continue;
      setState(() => _loadedTabs[index] = true);
    }
  }

  void _selectTab(int index) {
    if (!_loadedTabs[index]) {
      _loadedTabs[index] = true;
    }
    setState(() => _currentIndex = index);
  }

  void _handleHorizontalDragEnd(DragEndDetails details) {
    final velocity = details.primaryVelocity ?? 0;
    if (velocity.abs() < _swipeVelocityThreshold) return;

    if (velocity < 0 && _currentIndex < 3) {
      _selectTab(_currentIndex + 1);
      return;
    }

    if (velocity > 0 && _currentIndex > 0) {
      _selectTab(_currentIndex - 1);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onHorizontalDragEnd: _handleHorizontalDragEnd,
              child: Stack(
                children: List.generate(4, (index) {
                  if (!_loadedTabs[index]) {
                    return const SizedBox.shrink();
                  }

                  return Offstage(
                    offstage: _currentIndex != index,
                    child: TickerMode(
                      enabled: _currentIndex == index,
                      child: KeyedSubtree(
                        key: ValueKey(index),
                        child: _buildScreen(index),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 18,
            child: SafeArea(
              top: false,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(30),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      theme.elevatedSurface.withValues(
                        alpha: theme.isDarkMode ? 0.96 : 0.985,
                      ),
                      theme.subtleSurface.withValues(
                        alpha: theme.isDarkMode ? 0.88 : 0.94,
                      ),
                    ],
                  ),
                  border: Border.all(
                    color: theme.softBorder.withValues(
                      alpha: theme.isDarkMode ? 0.82 : 0.68,
                    ),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(
                        alpha: theme.isDarkMode ? 0.34 : 0.12,
                      ),
                      blurRadius: 34,
                      spreadRadius: 1,
                      offset: const Offset(0, 18),
                    ),
                    BoxShadow(
                      color: theme.colorScheme.primary.withValues(
                        alpha: theme.isDarkMode ? 0.07 : 0.04,
                      ),
                      blurRadius: 22,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(30),
                  child: NavigationBar(
                    selectedIndex: _currentIndex,
                    animationDuration: const Duration(milliseconds: 240),
                    onDestinationSelected: _selectTab,
                    labelBehavior:
                        NavigationDestinationLabelBehavior.onlyShowSelected,
                    destinations: const [
                      NavigationDestination(
                        icon: Icon(Icons.folder_outlined),
                        selectedIcon: Icon(Icons.folder_rounded),
                        label: 'Home',
                      ),
                      NavigationDestination(
                        icon: Icon(Icons.favorite_outline_rounded),
                        selectedIcon: Icon(Icons.favorite_rounded),
                        label: 'Favorites',
                      ),
                      NavigationDestination(
                        icon: Icon(Icons.history_rounded),
                        selectedIcon: Icon(Icons.history_toggle_off_rounded),
                        label: 'History',
                      ),
                      NavigationDestination(
                        icon: Icon(Icons.settings_outlined),
                        selectedIcon: Icon(Icons.settings_rounded),
                        label: 'Settings',
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildScreen(int index) {
    switch (index) {
      case 0:
        return const HomeScreen();
      case 1:
        return const FavoritesScreen();
      case 2:
        return const HistoryScreen();
      case 3:
        return const SettingsScreen();
      default:
        return const HomeScreen();
    }
  }
}
