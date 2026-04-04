import 'package:flutter/material.dart';
import '../../../history/services/history_service.dart';
import '../../../library/domain/entities/video_file.dart';
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
    state.subtitleFontFamily = SubtitleSettingsService.fontFamily;
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

  void setSubtitleFontFamily(String family) {
    state.subtitleFontFamily = family;
    SubtitleSettingsService.setFontFamily(family);
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

  Future<void> loadSubtitleTracksWithRestore({VideoFile? video}) async {
    final tracks = repository.getSubtitleTracks();
    state.subtitleTracks = tracks;
    if (tracks.isEmpty) {
      state.currentSubtitleTrackIndex = -1;
      state.subtitlesEnabled = false;
    } else if (video != null) {
      final savedTrack =
          await HistoryService.getSelectedSubtitleTrack(video.id);
      if (savedTrack != null && savedTrack.isNotEmpty) {
        final idx = tracks.indexWhere((t) {
          final title = t.title?.trim();
          final lang = t.language?.trim();
          final label = title != null && title.isNotEmpty
              ? title
              : lang != null && lang.isNotEmpty
                  ? lang.toUpperCase()
                  : null;
          return label == savedTrack;
        });
        if (idx >= 0) {
          state.currentSubtitleTrackIndex = idx;
          state.subtitlesEnabled = true;
          await repository.setSubtitleTrack(idx);
        }
      } else if (state.currentSubtitleTrackIndex >= tracks.length) {
        state.currentSubtitleTrackIndex = 0;
      } else if (state.currentSubtitleTrackIndex == -1 &&
          state.subtitlesEnabled) {
        state.currentSubtitleTrackIndex = 0;
      }
    } else if (state.currentSubtitleTrackIndex >= tracks.length) {
      state.currentSubtitleTrackIndex = 0;
    } else if (state.currentSubtitleTrackIndex == -1 &&
        state.subtitlesEnabled) {
      state.currentSubtitleTrackIndex = 0;
    }
    notifyListeners();
  }

  Future<void> setSubtitleTrack(int index, {VideoFile? video}) async {
    if (index < 0 || index >= state.subtitleTracks.length) return;

    state.currentSubtitleTrackIndex = index;
    state.subtitlesEnabled = true;
    SubtitleSettingsService.setEnabled(true);
    repository.setSubtitleTrack(index);
    if (video != null) {
      final track = state.subtitleTracks[index];
      final title = track.title?.trim();
      final lang = track.language?.trim();
      final label = title != null && title.isNotEmpty
          ? title
          : lang != null && lang.isNotEmpty
              ? lang.toUpperCase()
              : 'Subtitle ${index + 1}';
      await HistoryService.saveSelectedSubtitleTrack(video.id, label);
    }
    notifyListeners();
  }

  Future<void> loadExternalSubtitle(String path, {VideoFile? video}) async {
    await repository.loadExternalSubtitle(path);
    loadSubtitleTracks();
    state.subtitlesEnabled = true;
    SubtitleSettingsService.setEnabled(true);
    if (video != null) {
      try {
        final existing = await HistoryService.getSubtitlePaths(video.id);
        if (!existing.contains(path)) {
          await HistoryService.saveSubtitlePaths(video.id, [...existing, path]);
        }
      } catch (_) {}
    }
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
