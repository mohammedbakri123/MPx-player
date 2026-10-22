import 'package:flutter/material.dart';

import '../core/theme/app_theme.dart';
import '../features/library/presentation/screens/home_screen.dart';

class MPxApp extends StatelessWidget {
  const MPxApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MPx',
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      home: const HomeScreen(),
    );
  }
}
