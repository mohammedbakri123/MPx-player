import 'package:flutter/material.dart';

import '../services/app_settings_service.dart';

class AppSettingsController extends ChangeNotifier {
  AppThemePreference _themePreference = AppSettingsService.themePreference;
  PlayerPreset _playerPreset = AppSettingsService.playerPreset;
  bool _advancedOptionsEnabled = AppSettingsService.advancedOptionsEnabled;
  bool _autoResumePlayback = AppSettingsService.autoResumePlaybackSetting;
  bool _keepScreenAwake = AppSettingsService.keepScreenAwakeSetting;
  bool _swipeGestures = AppSettingsService.swipeGesturesSetting;
  bool _holdToBoost = AppSettingsService.holdToBoostSetting;

  AppThemePreference get themePreference => _themePreference;
  ThemeMode get themeMode => AppSettingsService.themeMode;
  PlayerPreset get playerPreset => _playerPreset;
  bool get advancedOptionsEnabled => _advancedOptionsEnabled;
  bool get autoResumePlayback => _autoResumePlayback;
  bool get keepScreenAwake => _keepScreenAwake;
  bool get swipeGestures => _swipeGestures;
  bool get holdToBoost => _holdToBoost;

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

  Future<void> setAdvancedOptionsEnabled(bool value) async {
    _advancedOptionsEnabled = value;
    await AppSettingsService.setAdvancedOptionsEnabled(value);
    notifyListeners();
  }

  Future<void> setAutoResumePlayback(bool value) async {
    _autoResumePlayback = value;
    await AppSettingsService.setAutoResumePlayback(value);
    notifyListeners();
  }

  Future<void> setKeepScreenAwake(bool value) async {
    _keepScreenAwake = value;
    await AppSettingsService.setKeepScreenAwake(value);
    notifyListeners();
  }

  Future<void> setSwipeGestures(bool value) async {
    _swipeGestures = value;
    await AppSettingsService.setSwipeGesturesSetting(value);
    notifyListeners();
  }

  Future<void> setHoldToBoost(bool value) async {
    _holdToBoost = value;
    await AppSettingsService.setHoldToBoostSetting(value);
    notifyListeners();
  }
}
