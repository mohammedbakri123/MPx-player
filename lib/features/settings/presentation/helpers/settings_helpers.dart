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

class SettingsVideoPerformanceHelpers {
  static IconData getIcon(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
        return Icons.energy_savings_leaf_outlined;
      case VideoPerformancePreset.balanced:
        return Icons.tune_rounded;
      case VideoPerformancePreset.instantSeeking:
        return Icons.bolt_rounded;
      case VideoPerformancePreset.quality:
        return Icons.hd_rounded;
      case VideoPerformancePreset.smoothMotion:
        return Icons.motion_photos_on_rounded;
      case VideoPerformancePreset.streaming:
        return Icons.wifi_tethering_rounded;
      case VideoPerformancePreset.softwareDecoding:
        return Icons.memory_rounded;
    }
  }

  static String getLabel(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
        return 'Power Saver';
      case VideoPerformancePreset.balanced:
        return 'Balanced';
      case VideoPerformancePreset.instantSeeking:
        return 'Instant Seek';
      case VideoPerformancePreset.quality:
        return 'Quality';
      case VideoPerformancePreset.smoothMotion:
        return 'Smooth Motion';
      case VideoPerformancePreset.streaming:
        return 'Streaming';
      case VideoPerformancePreset.softwareDecoding:
        return 'Software Decode';
    }
  }

  static String getDescription(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
        return 'Lower heat and battery use on weaker devices.';
      case VideoPerformancePreset.balanced:
        return 'Safe everyday playback with decent quality.';
      case VideoPerformancePreset.instantSeeking:
        return 'Fast local scrubbing and quicker jump response.';
      case VideoPerformancePreset.quality:
        return 'Sharper output for capable devices and big files.';
      case VideoPerformancePreset.smoothMotion:
        return 'Interpolation-focused playback for extra fluid motion.';
      case VideoPerformancePreset.streaming:
        return 'Bigger cache for unstable network playback.';
      case VideoPerformancePreset.softwareDecoding:
        return 'Compatibility fallback when hardware decode misbehaves.';
    }
  }

  static String getBadge(VideoPerformancePreset preset) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
        return 'Cool';
      case VideoPerformancePreset.balanced:
        return 'Default';
      case VideoPerformancePreset.instantSeeking:
        return 'Fast';
      case VideoPerformancePreset.quality:
        return 'Sharp';
      case VideoPerformancePreset.smoothMotion:
        return 'Fluid';
      case VideoPerformancePreset.streaming:
        return 'Buffer';
      case VideoPerformancePreset.softwareDecoding:
        return 'Fallback';
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
