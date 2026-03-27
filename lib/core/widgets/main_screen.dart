import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
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
  static const double _dockBottomOffset = 10;
  static const double _dockHideOffset = -100;

  int _currentIndex = 0;
  // Updated _loadedTabs size from 4 to 5
  final List<bool> _loadedTabs = [true, false, false, false, false];
  late final AnimationController _swipeController;
  Animation<double>? _swipeAnimation;
  double _dragOffset = 0;
  int? _pendingTabIndex;

  bool _isDockVisible = true;
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

    return NotificationListener<UserScrollNotification>(
      onNotification: (notification) {
        if (notification.direction == ScrollDirection.forward) {
          if (!_isDockVisible) setState(() => _isDockVisible = true);
        } else if (notification.direction == ScrollDirection.reverse) {
          if (_isDockVisible) setState(() => _isDockVisible = false);
        }
        return false;
      },
      child: Stack(
        // Updated List.generate count to use dynamic tab count
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
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bool isKeyboardVisible = MediaQuery.of(context).viewInsets.bottom > 0;

    return Scaffold(
      backgroundColor: Colors.transparent,
      resizeToAvoidBottomInset: false,
      body: Stack(
        children: [
          Positioned.fill(
            child: LayoutBuilder(
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
          ),
          AnimatedPositioned(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOutCubic,
            left: 14,
            right: 14,
            bottom: (_currentIndex == 1 || !_isDockVisible || isKeyboardVisible)
                ? _dockHideOffset
                : _dockBottomOffset,
            child: SafeArea(
              top: false,
              minimum: EdgeInsets.zero,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(34),
                  color: theme.elevatedSurface.withValues(
                    alpha: theme.isDarkMode ? 0.94 : 0.98,
                  ),
                  border: Border.all(
                    color: theme.softBorder.withValues(
                      alpha: theme.isDarkMode ? 0.82 : 0.9,
                    ),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(
                        alpha: theme.isDarkMode ? 0.3 : 0.1,
                      ),
                      blurRadius: 28,
                      spreadRadius: 0,
                      offset: const Offset(0, 14),
                    ),
                    BoxShadow(
                      color: theme.colorScheme.primary.withValues(
                        alpha: theme.isDarkMode ? 0.06 : 0.05,
                      ),
                      blurRadius: 18,
                      offset: const Offset(0, 6),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(34),
                  child: _buildCustomBottomBar(theme),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCustomBottomBar(ThemeData theme) {
    return SizedBox(
      height: 76,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(6, 6, 6, 8),
        child: Row(
          // Updated List.generate count to use dynamic tab count
          children: List.generate(_labels.length, (index) {
            final isSelected = _currentIndex == index;
            final iconData =
                isSelected ? _selectedIcons[index] : _outlineIcons[index];
            final label = _labels[index];

            return Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 2),
                child: InkWell(
                  onTap: () => _selectTab(index),
                  borderRadius: BorderRadius.circular(22),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOutCubic,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(22),
                      color: isSelected
                          ? theme.colorScheme.primaryContainer.withValues(
                              alpha: theme.isDarkMode ? 0.3 : 0.82,
                            )
                          : Colors.transparent,
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          iconData,
                          size: isSelected ? 23 : 21,
                          color: isSelected
                              ? theme.colorScheme.primary
                              : theme.faintText,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          label,
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight:
                                isSelected ? FontWeight.w700 : FontWeight.w500,
                            color: isSelected
                                ? theme.colorScheme.primary
                                : theme.faintText,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
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
