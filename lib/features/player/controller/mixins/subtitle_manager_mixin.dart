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
    state.subtitleFontWeight = SubtitleSettingsService.fontWeight;
    state.subtitleBottomPadding = SubtitleSettingsService.bottomPadding;
    state.subtitleBackgroundOpacity = SubtitleSettingsService.backgroundOpacity;
  }

  void toggleSubtitles(bool value) {
    state.subtitlesEnabled = value;
    SubtitleSettingsService.setEnabled(value);
    if (value) {
      if (state.currentSubtitleTrackIndex >= 0 &&
          state.currentSubtitleTrackIndex < state.subtitleTracks.length) {
        repository.setSubtitleTrack(state.currentSubtitleTrackIndex);
      } else {
        repository.enableSubtitles();
      }
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

  void setSubtitleFontWeight(FontWeight weight) {
    state.subtitleFontWeight = weight;
    SubtitleSettingsService.setFontWeight(weight);
    notifyListeners();
  }

  void setSubtitleBottomPadding(double padding) {
    state.subtitleBottomPadding = padding;
    SubtitleSettingsService.setBottomPadding(padding);
    notifyListeners();
  }

  void setSubtitleBackgroundOpacity(double opacity) {
    state.subtitleBackgroundOpacity = opacity;
    SubtitleSettingsService.setBackgroundOpacity(opacity);
    notifyListeners();
  }

  void loadSubtitleTracks() {
    final tracks = repository.getSubtitleTracks();
    state.subtitleTracks = tracks;
    if (tracks.isEmpty) {
      state.currentSubtitleTrackIndex = -1;
      state.subtitlesEnabled = false;
    } else if (state.currentSubtitleTrackIndex >= tracks.length) {
      state.currentSubtitleTrackIndex = 0;
    } else if (state.currentSubtitleTrackIndex == -1 &&
        state.subtitlesEnabled) {
      state.currentSubtitleTrackIndex = 0;
    }
    notifyListeners();
  }

  void setSubtitleTrack(int index) {
    if (index < 0 || index >= state.subtitleTracks.length) return;

    state.currentSubtitleTrackIndex = index;
    state.subtitlesEnabled = true;
    SubtitleSettingsService.setEnabled(true);
    repository.setSubtitleTrack(index);
    notifyListeners();
  }

  Future<void> applySubtitleSettings() async {
    if (state.subtitlesEnabled) {
      if (state.currentSubtitleTrackIndex >= 0 &&
          state.currentSubtitleTrackIndex < state.subtitleTracks.length) {
        await repository.setSubtitleTrack(state.currentSubtitleTrackIndex);
      } else {
        await repository.enableSubtitles();
      }
    }
  }
}
