import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_mpv/flutter_mpv.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'features/downloader/controller/downloader_controller.dart';
import 'features/downloader/services/downloader_settings_service.dart';
import 'features/settings/controllers/app_settings_controller.dart';
import 'features/settings/services/app_settings_service.dart';
import 'features/settings/services/subtitle_settings_service.dart';
import 'features/favorites/services/favorites_service.dart';
import 'features/library/services/thumbnail_cache.dart';
import 'features/library/services/library_preferences_service.dart';
import 'features/library/controller/file_browser_controller.dart';
import 'features/library/controller/library_view_controller.dart';
import 'features/library/presentation/widgets/home/home_fab.dart';
import 'features/splash/presentation/screens/splash_screen.dart';
import 'features/reels/controllers/reels_controller.dart';
import 'core/widgets/permission_wrapper.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Future.wait([
    AppSettingsService.init(),
    SubtitleSettingsService.init(),
    FavoritesService.init(),
    DownloaderSettingsService.init(),
    LibraryPreferencesService.init(),
  ]);

  ThumbnailCache().cleanup();

  FlutterMpv.ensureInitialized();

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

    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: _settingsController!),
        ChangeNotifierProvider(create: (_) => DownloaderController()),
        ChangeNotifierProvider(create: (_) => ReelsController()),
        ChangeNotifierProvider.value(value: FileBrowserController()),
        ChangeNotifierProvider(create: (_) => LibraryViewController()),
      ],
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
