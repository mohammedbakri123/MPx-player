import 'dart:ui' show Color, FontWeight;
import '../domain/repositories/player_repository.dart'
    show AudioTrackInfo, SubtitleTrackInfo;

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
  double speedBeforeLongPress = 1.0;
  bool isLongPressing = false;

  bool isLocked = false;

  AspectRatioMode aspectRatioMode = AspectRatioMode.fit;
  RepeatMode repeatMode = RepeatMode.off;

  List<AudioTrackInfo> audioTracks = [];
  int currentAudioTrackIndex = 0;

  List<SubtitleTrackInfo> subtitleTracks = [];
  int currentSubtitleTrackIndex = -1;

  bool subtitlesEnabled = true;
  double subtitleFontSize = 24.0;
  Color subtitleColor = const Color(0xFFFFFFFF);
  String subtitleFontFamily = 'Roboto';
  bool subtitleHasBackground = true;
  FontWeight subtitleFontWeight = FontWeight.w500;
  double subtitleBottomPadding = 24.0;
  double subtitleBackgroundOpacity = 0.7;

  double dragStartX = 0;
  bool isDraggingX = false;
  bool isDraggingY = false;
  String verticalDragSide = '';
  Duration seekStartPosition = Duration.zero;

  bool showSeekIndicator = false;
  String seekDirection = '';
  Duration seekDelta = Duration.zero;
  bool showVolumeIndicator = false;
  bool showBrightnessIndicator = false;
  double brightnessValue = 100.0;

  bool showDoubleTapSeekLeft = false;
  bool showDoubleTapSeekRight = false;

  int hideControlsRequestId = 0;
  int controlsInteractionCount = 0;

  int doubleTapSeekStep = 10;
  double dragSeekSensitivity = 0.3;
}
