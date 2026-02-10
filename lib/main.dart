import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:provider/provider.dart';
import 'features/library/controller/library_controller.dart';
import 'features/library/data/datasources/local_video_scanner.dart';
import 'core/widgets/permission_wrapper.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ),
  );

  runApp(const MPxPlayer());
}

class MPxPlayer extends StatelessWidget {
  const MPxPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    // Provide LibraryController at the app level so it persists across navigation
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
        home: const PermissionWrapper(),
      ),
    );
  }
}
