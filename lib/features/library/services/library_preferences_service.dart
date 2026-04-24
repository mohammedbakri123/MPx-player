import 'dart:async';
import 'package:shared_preferences/shared_preferences.dart';
import '../domain/enums/library_view_mode.dart';
import '../controller/file_browser_controller.dart';

class LibraryPreferencesService {
  static const _viewModeKey = 'library_view_mode';
  static const _sortByKey = 'library_sort_by';
  static const _sortOrderKey = 'library_sort_order';
  static const _showOnlyVideosKey = 'library_show_only_videos';

  static late SharedPreferences _prefs;

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  static LibraryViewMode get viewMode {
    final raw = _prefs.getString(_viewModeKey);
    return LibraryViewMode.values.firstWhere(
      (e) => e.name == raw,
      orElse: () => LibraryViewMode.list,
    );
  }

  static Future<void> setViewMode(LibraryViewMode mode) async {
    await _prefs.setString(_viewModeKey, mode.name);
  }

  static SortBy get sortBy {
    final raw = _prefs.getString(_sortByKey);
    return SortBy.values.firstWhere(
      (e) => e.name == raw,
      orElse: () => SortBy.name,
    );
  }

  static Future<void> setSortBy(SortBy sortBy) async {
    await _prefs.setString(_sortByKey, sortBy.name);
  }

  static SortOrder get sortOrder {
    final raw = _prefs.getString(_sortOrderKey);
    return SortOrder.values.firstWhere(
      (e) => e.name == raw,
      orElse: () => SortOrder.ascending,
    );
  }

  static Future<void> setSortOrder(SortOrder sortOrder) async {
    await _prefs.setString(_sortOrderKey, sortOrder.name);
  }

  static bool get showOnlyVideos {
    return _prefs.getBool(_showOnlyVideosKey) ?? true;
  }

  static Future<void> setShowOnlyVideos(bool value) async {
    await _prefs.setBool(_showOnlyVideosKey, value);
  }
}
