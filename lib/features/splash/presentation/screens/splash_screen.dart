import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../../core/services/logger_service.dart';
import '../../../library/controller/library_controller.dart';
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

  /// Pre-load video data in background - OPTIMIZED
  Future<void> _preloadData() async {
    try {
      final stopwatch = Stopwatch()..start();
      
      final controller = context.read<LibraryController>();
      
      // Check if we already have data in memory (app resume, not cold start)
      if (controller.folders.isNotEmpty) {
        AppLogger.i('⚡ Using existing in-memory data - instant!');
        _dataLoaded = true;
        return;
      }
      
      // Load from cache ONLY - no scanning!
      // Cache will be populated from previous session
      await controller.load();
      
      stopwatch.stop();
      _dataLoaded = true;
      
      AppLogger.i('⚡ Preload complete in ${stopwatch.elapsedMilliseconds}ms');
    } catch (e) {
      AppLogger.e('Error preloading data: $e');
      _dataLoaded = true; // Continue anyway
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          const SplashBackground(),
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
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SplashLogo(),
                SplashTitle(),
                SizedBox(height: 8),
                SplashSubtitle(),
              ],
            ),
          ),
          SplashProgressBar(animation: _progressAnimation),
        ],
      ),
    );
  }
}
