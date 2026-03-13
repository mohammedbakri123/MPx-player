import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme_tokens.dart';
import '../../../../core/services/logger_service.dart';
import '../widgets/splash_background.dart';
import '../widgets/splash_logo.dart';
import '../widgets/splash_title.dart';
import '../widgets/splash_subtitle.dart';
import '../widgets/splash_progress_bar.dart';

class SplashScreen extends StatefulWidget {
  final VoidCallback onComplete;

  const SplashScreen({super.key, required this.onComplete});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;
  late Animation<double> _scaleAnimation;
  late Animation<double> _progressAnimation;

  bool _dataLoaded = false;

  @override
  void initState() {
    super.initState();

    _controller = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );

    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.0, 0.4, curve: Curves.easeOut),
      ),
    );

    _scaleAnimation = Tween<double>(begin: 0.8, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.0, 0.4, curve: Curves.easeOut),
      ),
    );

    _progressAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.2, 1.0, curve: Curves.easeInOut),
      ),
    );

    // Pre-load data in background
    _preloadData();

    // Listen for animation completion
    _controller.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        // Wait for data if not ready yet
        if (_dataLoaded) {
          widget.onComplete();
        } else {
          // Data still loading, wait then complete
          Future.delayed(const Duration(milliseconds: 500), () {
            widget.onComplete();
          });
        }
      }
    });

    _controller.forward();
  }

  /// Pre-load step - no scanning needed, directory browsing is on-demand
  Future<void> _preloadData() async {
    AppLogger.i(
        'Splash: No scanning needed — using on-demand directory browser');
    _dataLoaded = true;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.appBackground,
      body: Stack(
        fit: StackFit.expand,
        children: [
          const SplashBackground(),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
              child: Column(
                children: [
                  const Spacer(),
                  AnimatedBuilder(
                    animation: _controller,
                    builder: (context, child) {
                      return FadeTransition(
                        opacity: _fadeAnimation,
                        child: ScaleTransition(
                          scale: _scaleAnimation,
                          child: child,
                        ),
                      );
                    },
                    child: const Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SplashLogo(),
                        SplashTitle(),
                        SizedBox(height: 10),
                        SplashSubtitle(),
                      ],
                    ),
                  ),
                  const Spacer(),
                  SplashProgressBar(animation: _progressAnimation),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
