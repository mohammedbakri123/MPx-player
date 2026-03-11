import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../player/controller/player_state.dart';

enum AppThemePreference { system, light, dark }

enum PlayerPreset { balanced, cinema, binge }

class AppSettingsService {
  static const String _themePreferenceKey = 'app_theme_preference';
  static const String _playerPresetKey = 'player_preset';
  static const String _advancedOptionsEnabledKey = 'advanced_options_enabled';
  static const String _autoResumePlaybackKey = 'advanced_auto_resume';
  static const String _keepScreenAwakeKey = 'advanced_keep_screen_awake';
  static const String _swipeGesturesKey = 'advanced_swipe_gestures';
  static const String _holdToBoostKey = 'advanced_hold_to_boost';

  static late SharedPreferences _prefs;

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  static AppThemePreference get themePreference {
    final value = _prefs.getString(_themePreferenceKey);
    switch (value) {
      case 'light':
        return AppThemePreference.light;
      case 'dark':
        return AppThemePreference.dark;
      default:
        return AppThemePreference.system;
    }
  }

  static ThemeMode get themeMode {
    switch (themePreference) {
      case AppThemePreference.light:
        return ThemeMode.light;
      case AppThemePreference.dark:
        return ThemeMode.dark;
      case AppThemePreference.system:
        return ThemeMode.system;
    }
  }

  static PlayerPreset get playerPreset {
    final value = _prefs.getString(_playerPresetKey);
    switch (value) {
      case 'cinema':
        return PlayerPreset.cinema;
      case 'binge':
        return PlayerPreset.binge;
      default:
        return PlayerPreset.balanced;
    }
  }

  static bool get advancedOptionsEnabled =>
      _prefs.getBool(_advancedOptionsEnabledKey) ?? false;

  static bool get autoResumePlaybackSetting =>
      _prefs.getBool(_autoResumePlaybackKey) ?? true;

  static bool get keepScreenAwakeSetting =>
      _prefs.getBool(_keepScreenAwakeKey) ?? true;

  static bool get swipeGesturesSetting =>
      _prefs.getBool(_swipeGesturesKey) ?? true;

  static bool get holdToBoostSetting => _prefs.getBool(_holdToBoostKey) ?? true;

  static bool get autoResumePlayback =>
      advancedOptionsEnabled && autoResumePlaybackSetting;

  static bool get keepScreenAwake =>
      advancedOptionsEnabled && keepScreenAwakeSetting;

  static bool get swipeGesturesEnabled =>
      advancedOptionsEnabled && swipeGesturesSetting;

  static bool get holdToBoostEnabled =>
      advancedOptionsEnabled && holdToBoostSetting;

  static double get presetPlaybackSpeed {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return 1.0;
      case PlayerPreset.cinema:
        return 1.0;
      case PlayerPreset.binge:
        return 1.15;
    }
  }

  static AspectRatioMode get presetAspectRatioMode {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return AspectRatioMode.fit;
      case PlayerPreset.cinema:
        return AspectRatioMode.fill;
      case PlayerPreset.binge:
        return AspectRatioMode.fit;
    }
  }

  static RepeatMode get presetRepeatMode {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return RepeatMode.off;
      case PlayerPreset.cinema:
        return RepeatMode.one;
      case PlayerPreset.binge:
        return RepeatMode.all;
    }
  }

  static Future<bool> setThemePreference(AppThemePreference value) {
    return _prefs.setString(_themePreferenceKey, value.name);
  }

  static Future<bool> setPlayerPreset(PlayerPreset value) {
    return _prefs.setString(_playerPresetKey, value.name);
  }

  static Future<bool> setAdvancedOptionsEnabled(bool value) {
    return _prefs.setBool(_advancedOptionsEnabledKey, value);
  }

  static Future<bool> setAutoResumePlayback(bool value) {
    return _prefs.setBool(_autoResumePlaybackKey, value);
  }

  static Future<bool> setKeepScreenAwake(bool value) {
    return _prefs.setBool(_keepScreenAwakeKey, value);
  }

  static Future<bool> setSwipeGesturesSetting(bool value) {
    return _prefs.setBool(_swipeGesturesKey, value);
  }

  static Future<bool> setHoldToBoostSetting(bool value) {
    return _prefs.setBool(_holdToBoostKey, value);
  }
}
