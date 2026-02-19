import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';

class SubtitleSettingsService {
  static const String _enabledKey = 'subtitle_enabled';
  static const String _fontSizeKey = 'subtitle_font_size';
  static const String _colorAlphaKey = 'subtitle_color_alpha';
  static const String _colorRedKey = 'subtitle_color_red';
  static const String _colorGreenKey = 'subtitle_color_green';
  static const String _colorBlueKey = 'subtitle_color_blue';
  static const String _backgroundKey = 'subtitle_background';

  static late SharedPreferences _prefs;

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  // Getters with defaults
  static bool get isEnabled => _prefs.getBool(_enabledKey) ?? true;
  static double get fontSize => _prefs.getDouble(_fontSizeKey) ?? 24.0;
  static Color get color {
    final alpha = _prefs.getInt(_colorAlphaKey) ?? 255;
    final red = _prefs.getInt(_colorRedKey) ?? 255;
    final green = _prefs.getInt(_colorGreenKey) ?? 255;
    final blue = _prefs.getInt(_colorBlueKey) ?? 255;
    return Color.fromARGB(alpha, red, green, blue);
  }
  static bool get hasBackground => _prefs.getBool(_backgroundKey) ?? true;

  // Setters
  static Future<bool> setEnabled(bool value) async {
    return await _prefs.setBool(_enabledKey, value);
  }

  static Future<bool> setFontSize(double value) async {
    return await _prefs.setDouble(_fontSizeKey, value);
  }

  static Future<bool> setColor(Color value) async {
    await _prefs.setInt(_colorAlphaKey, (value.a * 255).round().clamp(0, 255));
    await _prefs.setInt(_colorRedKey, (value.r * 255).round().clamp(0, 255));
    await _prefs.setInt(_colorGreenKey, (value.g * 255).round().clamp(0, 255));
    await _prefs.setInt(_colorBlueKey, (value.b * 255).round().clamp(0, 255));
    return Future.value(true);
  }

  static Future<bool> setHasBackground(bool value) async {
    return await _prefs.setBool(_backgroundKey, value);
  }

  // Reset to defaults
  static Future<void> resetToDefaults() async {
    await _prefs.remove(_enabledKey);
    await _prefs.remove(_fontSizeKey);
    await _prefs.remove(_colorAlphaKey);
    await _prefs.remove(_colorRedKey);
    await _prefs.remove(_colorGreenKey);
    await _prefs.remove(_colorBlueKey);
    await _prefs.remove(_backgroundKey);
  }

  // Get all settings as a map (useful for settings page)
  static Map<String, dynamic> getAllSettings() {
    return {
      'enabled': isEnabled,
      'fontSize': fontSize,
      'color': color.toARGB32(), // Using new API to avoid deprecated value property
      'hasBackground': hasBackground,
    };
  }

  // Set all settings from a map (useful for settings page)
  static Future<void> setAllSettings(Map<String, dynamic> settings) async {
    if (settings.containsKey('enabled')) {
      await setEnabled(settings['enabled']);
    }
    if (settings.containsKey('fontSize')) {
      await setFontSize(settings['fontSize']);
    }
    if (settings.containsKey('color')) {
      await setColor(Color(settings['color'])); // This will store the individual components
    }
    if (settings.containsKey('hasBackground')) {
      await setHasBackground(settings['hasBackground']);
    }
  }

  // Methods specifically designed for settings page integration
  static Map<String, dynamic> getSubtitleSettingsForPage() {
    return {
      'subtitle_enabled': isEnabled,
      'subtitle_font_size': fontSize,
      'subtitle_color': color.toARGB32(), // Using new API to avoid deprecated value property
      'subtitle_background': hasBackground,
    };
  }

  static Future<void> updateFromSettingsPage(Map<String, dynamic> settings) async {
    if (settings.containsKey('subtitle_enabled')) {
      await setEnabled(settings['subtitle_enabled']);
    }
    if (settings.containsKey('subtitle_font_size')) {
      await setFontSize(settings['subtitle_font_size']);
    }
    if (settings.containsKey('subtitle_color')) {
      await setColor(Color(settings['subtitle_color'])); // This will store the individual components
    }
    if (settings.containsKey('subtitle_background')) {
      await setHasBackground(settings['subtitle_background']);
    }
  }
  
  // Validation methods for settings page
  static bool isValidFontSize(double size) {
    return size >= 12.0 && size <= 48.0;
  }
  
  static List<Color> getDefaultColorOptions() {
    return [
      const Color(0xFFFFFFFF), // White
      const Color(0xFFFFFF00), // Yellow
      const Color(0xFF00FFFF), // Cyan
      const Color(0xFF00FF00), // Green
      const Color(0xFFFF0000), // Red
      const Color(0xFF0000FF), // Blue
      const Color(0xFFFF00FF), // Magenta
      const Color(0xFF000000), // Black
    ];
  }
}