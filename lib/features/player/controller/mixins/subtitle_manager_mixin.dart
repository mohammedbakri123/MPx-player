import 'package:flutter/material.dart';
import '../../../settings/services/subtitle_settings_service.dart';
import '../../domain/repositories/player_repository.dart';
import '../player_state.dart';

/// Mixin for managing subtitle settings and state.
mixin SubtitleManagerMixin on ChangeNotifier {
  PlayerRepository get repository;
  PlayerState get state;

  void initializeSubtitles() {
    state.subtitlesEnabled = SubtitleSettingsService.isEnabled;
    state.subtitleFontSize = SubtitleSettingsService.fontSize;
    state.subtitleColor = SubtitleSettingsService.color;
    state.subtitleHasBackground = SubtitleSettingsService.hasBackground;
  }

  void toggleSubtitles(bool value) {
    state.subtitlesEnabled = value;
    SubtitleSettingsService.setEnabled(value);
    if (value) {
      repository.enableSubtitles();
    } else {
      repository.disableSubtitles();
    }
    notifyListeners();
  }

  void setSubtitleFontSize(double size) {
    state.subtitleFontSize = size;
    SubtitleSettingsService.setFontSize(size);
    notifyListeners();
  }

  void setSubtitleColor(Color color) {
    state.subtitleColor = color;
    SubtitleSettingsService.setColor(color);
    notifyListeners();
  }

  void setSubtitleBackground(bool hasBackground) {
    state.subtitleHasBackground = hasBackground;
    SubtitleSettingsService.setHasBackground(hasBackground);
    notifyListeners();
  }

  Future<void> applySubtitleSettings() async {
    if (state.subtitlesEnabled) {
      await repository.enableSubtitles();
    }
  }
}
