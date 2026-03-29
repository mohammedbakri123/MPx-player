import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mpx/core/services/binary_manager.dart';
import 'package:mpx/core/services/downloader_platform_service.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:mpx/features/downloader/presentation/screens/downloads_manager_screen.dart';
import 'package:mpx/features/downloader/services/downloader_settings_service.dart';
import 'package:mpx/features/library/presentation/screens/home_screen.dart';
import 'package:mpx/features/favorites/presentation/screens/favorites_screen.dart';
import 'package:mpx/features/history/presentation/screens/history_screen.dart';
import 'package:mpx/features/settings/presentation/screens/settings_screen.dart';
import 'package:mpx/features/reels/presentation/screens/reels_screen.dart'; // New import for ReelsScreen

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen>
    with SingleTickerProviderStateMixin {
  static const double _swipeVelocityThreshold = 320;
  static const double _swipeProgressThreshold = 0.22;

  int _currentIndex = 0;
  // Updated _loadedTabs size from 4 to 5
  final List<bool> _loadedTabs = [true, false, false, false, false];
  late final AnimationController _swipeController;
  Animation<double>? _swipeAnimation;
  double _dragOffset = 0;
  int? _pendingTabIndex;

  StreamSubscription<String>? _sharedUrlSubscription;

  @override
  void initState() {
    super.initState();
    _swipeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 260),
    )
      ..addListener(() {
        final value = _swipeAnimation?.value;
        if (value == null) return;
        setState(() => _dragOffset = value);
      })
      ..addStatusListener((status) {
        if (status != AnimationStatus.completed) return;
        final pendingTabIndex = _pendingTabIndex;
        if (pendingTabIndex != null) {
          _selectTab(pendingTabIndex);
        }
        if (!mounted) return;
        setState(() {
          _dragOffset = 0;
          _pendingTabIndex = null;
        });
      });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _warmUpTabs();
      _consumeInitialSharedUrl();
    });
    _sharedUrlSubscription =
        DownloaderPlatformService.instance.sharedUrlEvents.listen(
      (url) {
        _openSharedUrl(url);
      },
    );
    if (DownloaderSettingsService.autoUpdateEnabled) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        unawaited(BinaryManager.instance.ensureBinariesAvailable());
        unawaited(
          BinaryManager.instance.checkForUpdates().catchError((_) {
            return BinaryManager.instance.status;
          }),
        );
      });
    }
  }

  @override
  void dispose() {
    _sharedUrlSubscription?.cancel();
    _swipeController.dispose();
    super.dispose();
  }

  Future<void> _consumeInitialSharedUrl() async {
    final url = await DownloaderPlatformService.instance.consumeSharedUrl();
    if (!mounted || url == null || url.isEmpty) {
      return;
    }
    _openSharedUrl(url);
  }

  Future<void> _openSharedUrl(String url) async {
    if (!mounted) {
      return;
    }

    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => DownloadsManagerScreen(initialSharedUrl: url),
      ),
    );
  }

  Future<void> _warmUpTabs() async {
    // Adjusted loop to cover all tabs dynamically
    for (int i = 1; i < _labels.length; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 140));
      if (!mounted || _loadedTabs[i]) continue;
      setState(() => _loadedTabs[i] = true);
    }
  }

  void _selectTab(int index) {
    if (!_loadedTabs[index]) {
      _loadedTabs[index] = true;
    }
    setState(() => _currentIndex = index);
  }

  void _handleHorizontalDragStart(DragStartDetails details) {
    if (_swipeController.isAnimating) {
      _swipeController.stop();
      _swipeAnimation = null;
      _pendingTabIndex = null;
    }
  }

  void _handleHorizontalDragUpdate(DragUpdateDetails details, double width) {
    if (_swipeController.isAnimating) return;

    final nextOffset =
        (_dragOffset + details.primaryDelta!).clamp(-width, width);
    // Updated hasTarget condition to use dynamic tab count
    final hasTarget = nextOffset < 0
        ? _currentIndex < _labels.length - 1
        : nextOffset > 0
            ? _currentIndex > 0
            : true;

    if (!hasTarget) {
      setState(() => _dragOffset = nextOffset * 0.18);
      return;
    }

    final targetIndex = nextOffset < 0 ? _currentIndex + 1 : _currentIndex - 1;
    if (nextOffset != 0) {
      _ensureTabLoaded(targetIndex);
    }

    setState(() => _dragOffset = nextOffset);
  }

  void _handleHorizontalDragEnd(DragEndDetails details, double width) {
    if (_swipeController.isAnimating) return;

    final velocity = details.primaryVelocity ?? 0;
    final progress = width == 0 ? 0.0 : _dragOffset.abs() / width;
    final shouldAdvance = velocity.abs() >= _swipeVelocityThreshold ||
        progress >= _swipeProgressThreshold;
    final targetIndex = _getTargetIndexForOffset(_dragOffset);

    if (shouldAdvance && targetIndex != null) {
      _animateDragTo(
        _dragOffset < 0 ? -width : width,
        pendingTabIndex: targetIndex,
      );
      return;
    }

    _animateDragTo(0);
  }

  void _ensureTabLoaded(int index) {
    if (index < 0 || index >= _loadedTabs.length || _loadedTabs[index]) return;
    setState(() => _loadedTabs[index] = true);
  }

  int? _getTargetIndexForOffset(double offset) {
    // Updated condition to use dynamic tab count and added curly braces
    if (offset < 0 && _currentIndex < _labels.length - 1) {
      return _currentIndex + 1;
    }
    if (offset > 0 && _currentIndex > 0) {
      return _currentIndex - 1;
    }
    return null;
  }

  void _animateDragTo(double targetOffset, {int? pendingTabIndex}) {
    _swipeController.stop();
    _pendingTabIndex = pendingTabIndex;
    _swipeAnimation = Tween<double>(
      begin: _dragOffset,
      end: targetOffset,
    ).animate(CurvedAnimation(
      parent: _swipeController,
      curve: Curves.easeOutCubic,
    ));
    _swipeController
      ..reset()
      ..forward();
  }

  Widget _buildTabLayers(double width) {
    final dragTargetIndex = _getTargetIndexForOffset(_dragOffset);

    return Stack(
      children: List.generate(_labels.length, (index) {
        if (!_loadedTabs[index]) {
          return const SizedBox.shrink();
        }

        final isCurrent = index == _currentIndex;
        final isDragTarget = dragTargetIndex == index;
        final shouldShow = isCurrent || isDragTarget;

        if (!shouldShow) {
          return Offstage(
            offstage: true,
            child: TickerMode(
              enabled: false,
              child: KeyedSubtree(
                key: ValueKey(index),
                child: _buildScreen(index),
              ),
            ),
          );
        }

        final offset = isCurrent
            ? _dragOffset
            : (_dragOffset < 0 ? width : -width) + _dragOffset;

        return Transform.translate(
          offset: Offset(offset, 0),
          child: TickerMode(
            enabled: isCurrent,
            child: KeyedSubtree(
              key: ValueKey(index),
              child: _buildScreen(index),
            ),
          ),
        );
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isReels = _currentIndex == 1;

    return Scaffold(
      backgroundColor: Colors.transparent,
      resizeToAvoidBottomInset: false,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;

          return GestureDetector(
            behavior: HitTestBehavior.translucent,
            onHorizontalDragStart: _handleHorizontalDragStart,
            onHorizontalDragUpdate: (details) =>
                _handleHorizontalDragUpdate(details, width),
            onHorizontalDragEnd: (details) =>
                _handleHorizontalDragEnd(details, width),
            child: ClipRect(
              child: _buildTabLayers(width),
            ),
          );
        },
      ),
      bottomNavigationBar: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          color: isReels ? Colors.black : theme.elevatedSurface,
          border: Border(
            top: BorderSide(
              color: isReels
                  ? Colors.white.withValues(alpha: 0.08)
                  : theme.softBorder,
              width: 0.5,
            ),
          ),
        ),
        child: NavigationBarTheme(
          data: NavigationBarThemeData(
            backgroundColor: Colors.transparent,
            surfaceTintColor: Colors.transparent,
            indicatorColor: isReels
                ? Colors.white.withValues(alpha: 0.12)
                : theme.colorScheme.primaryContainer.withValues(
                    alpha: theme.isDarkMode ? 0.28 : 0.7,
                  ),
            iconTheme: WidgetStateProperty.resolveWith((states) {
              if (states.contains(WidgetState.selected)) {
                return IconThemeData(
                  color: isReels ? Colors.white : theme.colorScheme.primary,
                  size: 24,
                );
              }
              return IconThemeData(
                color: isReels
                    ? Colors.white.withValues(alpha: 0.55)
                    : theme.faintText,
                size: 22,
              );
            }),
            labelTextStyle: WidgetStateProperty.resolveWith((states) {
              if (states.contains(WidgetState.selected)) {
                return TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: isReels ? Colors.white : theme.colorScheme.primary,
                );
              }
              return TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: isReels
                    ? Colors.white.withValues(alpha: 0.55)
                    : theme.faintText,
              );
            }),
            height: 68,
            elevation: 0,
            shadowColor: Colors.transparent,
          ),
          child: NavigationBar(
            selectedIndex: _currentIndex,
            onDestinationSelected: _selectTab,
            labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
            destinations: List.generate(_labels.length, (index) {
              return NavigationDestination(
                icon: Icon(_outlineIcons[index]),
                selectedIcon: Icon(_selectedIcons[index]),
                label: _labels[index],
              );
            }),
          ),
        ),
      ),
    );
  }

  // Updated static lists for icons and labels
  static const List<IconData> _outlineIcons = [
    Icons.folder_outlined,
    Icons.video_library_outlined, // Reels at index 1
    Icons.favorite_outline_rounded,
    Icons.history_rounded,
    Icons.settings_outlined,
  ];

  static const List<IconData> _selectedIcons = [
    Icons.folder_rounded,
    Icons.video_library_rounded, // Reels at index 1
    Icons.favorite_rounded,
    Icons.history_toggle_off_rounded,
    Icons.settings_rounded,
  ];

  static const List<String> _labels = [
    'Home',
    'Reels', // Reels at index 1
    'Favorites',
    'History',
    'Settings',
  ];

  Widget _buildScreen(int index) {
    switch (index) {
      case 0:
        return const HomeScreen();
      case 1: // New Reels Screen
        // We only consider Reels active if we are strictly on the Reels tab, with NO drag offset,
        // AND the drag animation is completely finished.
        final isStrictlyActive = _currentIndex == 1 &&
            _dragOffset == 0 &&
            _pendingTabIndex == null &&
            !_swipeController.isAnimating;
        return ReelsScreen(isActive: isStrictlyActive);
      case 2: // Shifted from 1
        return const FavoritesScreen();
      case 3: // Shifted from 2
        return const HistoryScreen();
      case 4: // Shifted from 3
        return const SettingsScreen();
      default:
        return const HomeScreen();
    }
  }
}
