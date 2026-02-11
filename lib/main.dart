import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:provider/provider.dart';
import 'core/services/subtitle_settings_service.dart';
import 'features/library/controller/library_controller.dart';
import 'features/library/data/datasources/local_video_scanner.dart';
import 'features/splash/presentation/screens/splash_screen.dart';
import 'core/widgets/permission_wrapper.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SubtitleSettingsService.init(); // Initialize subtitle settings service
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

  void _onSplashComplete() {
    setState(() {
      _showSplash = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => LibraryController(VideoScanner()),
      child: MaterialApp(
        title: 'MPx Player',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF6366F1),
            brightness: Brightness.light,
          ),
          useMaterial3: true,
          scaffoldBackgroundColor: const Color(0xFFF8FAFC),
        ),
        home: _showSplash
            ? SplashScreen(onComplete: _onSplashComplete)
            : const PermissionWrapper(),
      ),
    );
  }
}
