import 'package:flutter/material.dart';

import '../../services/app_settings_service.dart';

/// Helper functions for theme-related UI elements
class SettingsThemeHelpers {
  static IconData getIcon(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return Icons.brightness_auto_rounded;
      case AppThemePreference.light:
        return Icons.light_mode_rounded;
      case AppThemePreference.dark:
        return Icons.dark_mode_rounded;
    }
  }

  static String getLabel(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return 'Match device';
      case AppThemePreference.light:
        return 'Light';
      case AppThemePreference.dark:
        return 'Dark';
    }
  }

  static String getDescription(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.system:
        return 'Follow your phone settings';
      case AppThemePreference.light:
        return 'Clean daylight interface';
      case AppThemePreference.dark:
        return 'Low-glare night watching';
    }
  }
}

/// Helper functions for player preset UI elements
class SettingsPresetHelpers {
  static IconData getIcon(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return Icons.dashboard_customize_outlined;
      case PlayerPreset.cinema:
        return Icons.movie_creation_outlined;
      case PlayerPreset.binge:
        return Icons.local_fire_department_outlined;
    }
  }

  static String getLabel(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Balanced';
      case PlayerPreset.cinema:
        return 'Cinema';
      case PlayerPreset.binge:
        return 'Binge';
    }
  }

  static String getDescription(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Standard framing and a neutral watch setup';
      case PlayerPreset.cinema:
        return 'Immersive framing with repeat-one defaults';
      case PlayerPreset.binge:
        return 'Slightly faster playback and queue-friendly repeat';
    }
  }

  static String getSpeed(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return '1.0x';
      case PlayerPreset.cinema:
        return '1.0x';
      case PlayerPreset.binge:
        return '1.15x';
    }
  }

  static String getAspect(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Fit';
      case PlayerPreset.cinema:
        return 'Fill';
      case PlayerPreset.binge:
        return 'Fit';
    }
  }

  static String getRepeat(PlayerPreset preset) {
    switch (preset) {
      case PlayerPreset.balanced:
        return 'Off';
      case PlayerPreset.cinema:
        return 'One';
      case PlayerPreset.binge:
        return 'All';
    }
  }
}

/// Helper functions for font weight UI elements
class SettingsFontWeightHelpers {
  static String getLabel(FontWeight weight) {
    if (weight == FontWeight.w700) return 'Bold';
    if (weight == FontWeight.w400) return 'Regular';
    return 'Medium';
  }
}
