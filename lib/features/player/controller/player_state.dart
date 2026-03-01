import 'dart:ui' show Color;
import '../domain/repositories/player_repository.dart' show AudioTrackInfo;
import '../../settings/services/subtitle_settings_service.dart';

enum AspectRatioMode {
  fit,
  fill,
  stretch,
  ratio16x9,
  ratio4x3,
}

enum RepeatMode {
  off,
  one,
  all,
}

class PlayerState {
  bool isPlaying = true;
  bool showControls = true;
  bool isBuffering = false;
  bool isFullscreen = false;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  double volume = 100;
  double playbackSpeed = 1.0;
  bool isLongPressing = false;

  bool isLocked = false;

  AspectRatioMode aspectRatioMode = AspectRatioMode.fit;
  RepeatMode repeatMode = RepeatMode.off;

  List<AudioTrackInfo> audioTracks = [];
  int currentAudioTrackIndex = 0;

  // Subtitle settings with default values from SubtitleSettingsService
  bool _subtitlesEnabled = SubtitleSettingsService.isEnabled;
  double _subtitleFontSize = SubtitleSettingsService.fontSize;
  Color _subtitleColor = SubtitleSettingsService.color;
  bool _subtitleHasBackground = SubtitleSettingsService.hasBackground;

  // Getters for subtitle properties
  bool get subtitlesEnabled => _subtitlesEnabled;
  set subtitlesEnabled(bool value) => _subtitlesEnabled = value;

  double get subtitleFontSize => _subtitleFontSize;
  set subtitleFontSize(double value) => _subtitleFontSize = value;

  Color get subtitleColor => _subtitleColor;
  set subtitleColor(Color value) => _subtitleColor = value;

  bool get subtitleHasBackground => _subtitleHasBackground;
  set subtitleHasBackground(bool value) => _subtitleHasBackground = value;

  double dragStartX = 0;
  bool isDraggingX = false;
  bool isDraggingY = false;
  String verticalDragSide = '';
  Duration seekStartPosition = Duration.zero;

  bool showSeekIndicator = false;
  String seekDirection = '';
  bool showVolumeIndicator = false;
  bool showBrightnessIndicator = false;
  double brightnessValue = 0.5;

  bool showDoubleTapSeekLeft = false;
  bool showDoubleTapSeekRight = false;
}
