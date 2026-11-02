import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/theme/app_theme.dart';
import '../features/library/controller/library_controller.dart';
import 'main_shell.dart';

class MPxApp extends StatefulWidget {
  const MPxApp({super.key});

  @override
  State<MPxApp> createState() => _MPxAppState();
}

class _MPxAppState extends State<MPxApp> {
  // MX-style dark-first: the player opens dark, like MX.
  // The user can still switch to Light or System in Settings.
  ThemeMode _themeMode = ThemeMode.dark;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<LibraryController>(
      create: (_) => LibraryController(),
      child: MaterialApp(
        title: 'MPx',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: _themeMode,
        home: MpxMainShell(
          themeMode: _themeMode,
          onThemeModeChanged: (ThemeMode mode) {
            setState(() {
              _themeMode = mode;
            });
          },
        ),
      ),
    );
  }
}
