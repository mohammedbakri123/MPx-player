import 'dart:ui' show Color;
import '../domain/repositories/player_repository.dart' show AudioTrackInfo;

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

  late bool subtitlesEnabled;
  late double subtitleFontSize;
  late Color subtitleColor;
  late bool subtitleHasBackground;

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
