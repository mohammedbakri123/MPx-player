import 'dart:async';
import 'package:flutter/foundation.dart';
import '../domain/enums/library_view_mode.dart';
import '../services/library_preferences_service.dart';

class LibraryViewController extends ChangeNotifier {
  LibraryViewMode _viewMode;

  LibraryViewController() : _viewMode = LibraryPreferencesService.viewMode;

  LibraryViewMode get viewMode => _viewMode;

  void setViewMode(LibraryViewMode mode) {
    if (_viewMode == mode) return;
    _viewMode = mode;
    unawaited(LibraryPreferencesService.setViewMode(mode));
    notifyListeners();
  }
}
