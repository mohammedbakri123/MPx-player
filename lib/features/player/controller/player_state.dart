import 'dart:ui' show Color;

/// Holds all player state fields to keep the controller clean.
class PlayerState {
  // Playback state
  bool isPlaying = true;
  bool showControls = true;
  bool isBuffering = false;
  bool isFullscreen = false;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  double volume = 100;
  double playbackSpeed = 1.0;
  bool isLongPressing = false;

  // Subtitle state
  late bool subtitlesEnabled;
  late double subtitleFontSize;
  late Color subtitleColor;
  late bool subtitleHasBackground;

  // Gesture state
  double dragStartX = 0;
  bool isDraggingX = false;
  bool isDraggingY = false;
  String verticalDragSide = '';
  Duration seekStartPosition = Duration.zero;

  // UI indicators
  bool showSeekIndicator = false;
  String seekDirection = '';
  bool showVolumeIndicator = false;
  bool showBrightnessIndicator = false;
  double brightnessValue = 0.5;
}
