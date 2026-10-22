import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/theme/app_theme.dart';
import '../features/library/controller/library_controller.dart';
import '../features/library/data/datasources/media_library_datasource.dart';
import '../features/library/data/repositories/photo_library_repository.dart';
import '../features/library/presentation/screens/home_screen.dart';

class MPxApp extends StatelessWidget {
  const MPxApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<LibraryController>(
      create: (_) => LibraryController(
        PhotoLibraryRepository(MediaLibraryDatasource()),
      ),
      child: MaterialApp(
        title: 'MPx',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ThemeMode.system,
        home: const HomeScreen(),
      ),
    );
  }
}
