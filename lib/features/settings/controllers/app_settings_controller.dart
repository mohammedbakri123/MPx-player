import 'package:flutter/material.dart';

import '../services/app_settings_service.dart';

class AppSettingsController extends ChangeNotifier {
  AppThemePreference _themePreference = AppSettingsService.themePreference;
  PlayerPreset _playerPreset = AppSettingsService.playerPreset;
  VideoPerformancePreset _videoPerformancePreset =
      AppSettingsService.videoPerformancePreset;
  bool _expertEngineEnabled = AppSettingsService.expertEngineEnabled;
  ExpertEngineSettings _expertEngineSettings =
      AppSettingsService.expertEngineSettings;

  AppThemePreference get themePreference => _themePreference;
  ThemeMode get themeMode => AppSettingsService.themeMode;
  PlayerPreset get playerPreset => _playerPreset;
  VideoPerformancePreset get videoPerformancePreset => _videoPerformancePreset;
  bool get expertEngineEnabled => _expertEngineEnabled;
  ExpertEngineSettings get expertEngineSettings => _expertEngineSettings;

  Future<void> setThemePreference(AppThemePreference value) async {
    _themePreference = value;
    await AppSettingsService.setThemePreference(value);
    notifyListeners();
  }

  Future<void> setPlayerPreset(PlayerPreset value) async {
    _playerPreset = value;
    await AppSettingsService.setPlayerPreset(value);
    notifyListeners();
  }


  Future<void> setVideoPerformancePreset(VideoPerformancePreset value) async {
    _videoPerformancePreset = value;
    await AppSettingsService.setVideoPerformancePreset(value);
    notifyListeners();
  }

  Future<void> setExpertEngineEnabled(bool value) async {
    _expertEngineEnabled = value;
    await AppSettingsService.setExpertEngineEnabled(value);
    notifyListeners();
  }

  Future<void> setExpertEngineSettings(ExpertEngineSettings value) async {
    _expertEngineSettings = value;
    await AppSettingsService.setExpertEngineSettings(value);
    notifyListeners();
  }

  Future<void> resetExpertEngineSettingsFromPreset(
    VideoPerformancePreset preset,
  ) async {
    _videoPerformancePreset = preset;
    _expertEngineSettings = ExpertEngineSettings.fromPreset(preset);
    await AppSettingsService.setVideoPerformancePreset(preset);
    await AppSettingsService.setExpertEngineSettings(_expertEngineSettings);
    notifyListeners();
  }

}
