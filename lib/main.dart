import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'features/settings/controllers/app_settings_controller.dart';
import 'features/settings/services/app_settings_service.dart';
import 'features/settings/services/subtitle_settings_service.dart';
import 'features/favorites/services/favorites_service.dart';
import 'features/library/services/thumbnail_cache.dart';
import 'features/library/presentation/widgets/home/home_fab.dart';
import 'features/splash/presentation/screens/splash_screen.dart';
import 'core/widgets/permission_wrapper.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Future.wait([
    AppSettingsService.init(),
    SubtitleSettingsService.init(),
    FavoritesService.init(),
  ]);

  ThumbnailCache().cleanup();

  MediaKit.ensureInitialized();

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const MPxPlayer());
}

class MPxPlayer extends StatefulWidget {
  const MPxPlayer({super.key});

  @override
  State<MPxPlayer> createState() => _MPxPlayerState();
}

class _MPxPlayerState extends State<MPxPlayer> {
  bool _showSplash = true;
  AppSettingsController? _settingsController;

  void _onSplashComplete() {
    setState(() {
      _showSplash = false;
    });
  }

  @override
  void dispose() {
    _settingsController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _settingsController ??= AppSettingsController();

    return ChangeNotifierProvider.value(
      value: _settingsController!,
      child: Consumer<AppSettingsController>(
        builder: (context, settings, _) {
          final platformBrightness =
              WidgetsBinding.instance.platformDispatcher.platformBrightness;
          final isDark = settings.themeMode == ThemeMode.dark ||
              (settings.themeMode == ThemeMode.system &&
                  platformBrightness == Brightness.dark);

          SystemChrome.setSystemUIOverlayStyle(
            SystemUiOverlayStyle(
              statusBarColor: Colors.transparent,
              statusBarIconBrightness:
                  isDark ? Brightness.light : Brightness.dark,
              statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
            ),
          );

          return MaterialApp(
            title: 'MPx Player',
            debugShowCheckedModeBanner: false,
            navigatorObservers: [HomeFAB.routeObserver],
            theme: AppTheme.light(),
            darkTheme: AppTheme.dark(),
            themeMode: settings.themeMode,
            home: _showSplash
                ? SplashScreen(onComplete: _onSplashComplete)
                : const PermissionWrapper(),
          );
        },
      ),
    );
  }
}
