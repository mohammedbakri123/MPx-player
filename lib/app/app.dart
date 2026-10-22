import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/theme/app_theme.dart';
import '../features/library/controller/library_controller.dart';
import '../features/library/data/datasources/media_library_datasource.dart';
import '../features/library/data/repositories/file_system_library_repository.dart';
import '../features/library/data/repositories/photo_library_repository.dart';
import '../features/library/domain/repositories/library_repository.dart';
import '../features/library/data/datasources/file_system_library_datasource.dart';
import '../features/library/presentation/screens/home_screen.dart';

LibraryRepository _createLibraryRepository() {
  if (Platform.isAndroid || Platform.isIOS) {
    return PhotoLibraryRepository(MediaLibraryDatasource());
  }
  return FileSystemLibraryRepository(FileSystemLibraryDatasource());
}

class MPxApp extends StatelessWidget {
  const MPxApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<LibraryController>(
      create: (_) => LibraryController(_createLibraryRepository()),
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
