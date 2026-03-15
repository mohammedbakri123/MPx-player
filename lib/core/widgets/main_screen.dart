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

class _MainScreenState extends State<MainScreen>
    with SingleTickerProviderStateMixin {
  static const double _swipeVelocityThreshold = 320;
  static const double _swipeProgressThreshold = 0.22;
  static const double _dockBottomOffset = 4;
  int _currentIndex = 0;
  final List<bool> _loadedTabs = [true, false, false, false];
  late final AnimationController _swipeController;
  Animation<double>? _swipeAnimation;
  double _dragOffset = 0;
  int? _pendingTabIndex;

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
    });
  }

  @override
  void dispose() {
    _swipeController.dispose();
    super.dispose();
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
    final hasTarget = nextOffset < 0
        ? _currentIndex < 3
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
    if (offset < 0 && _currentIndex < 3) return _currentIndex + 1;
    if (offset > 0 && _currentIndex > 0) return _currentIndex - 1;
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
      children: List.generate(4, (index) {
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

    return Scaffold(
      backgroundColor: Colors.transparent,
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
          Positioned(
            left: 16,
            right: 16,
            bottom: _dockBottomOffset,
            child: SafeArea(
              top: false,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(30),
                  color: theme.elevatedSurface,
                  // gradient: LinearGradient(
                  //   begin: Alignment.topLeft,
                  //   end: Alignment.bottomRight,
                  //   colors: [
                  //     theme.elevatedSurface.withValues(
                  //       alpha: theme.isDarkMode ? 0.96 : 0.985,
                  //     // height: 65,   ),
                  //     theme.subtleSurface.withValues(
                  //       alpha: theme.isDarkMode ? 0.88 : 0.94,
                  //     ),
                  //   ],
                  // ),
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
