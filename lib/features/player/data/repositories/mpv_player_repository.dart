import 'dart:async';
import 'package:flutter_mpv/flutter_mpv.dart';
import 'package:mpx/features/settings/services/app_settings_service.dart';
import '../../domain/repositories/player_repository.dart';

class MpvPlayerRepository implements PlayerRepository {
  late final Player _player;
  bool _isDisposed = false;
  final StreamController<void> _audioTracksController =
      StreamController<void>.broadcast();
  final StreamController<void> _subtitleTracksController =
      StreamController<void>.broadcast();

  MpvPlayerRepository() {
    _player = Player(
      configuration: _configuration,
    );
    _setupTrackListeners();
  }

  void _setupTrackListeners() {
    _player.stream.tracks.listen((tracks) {
      if (!_audioTracksController.isClosed) {
        _audioTracksController.add(null);
      }
      if (!_subtitleTracksController.isClosed) {
        _subtitleTracksController.add(null);
      }
    });
  }

  PlayerConfiguration get _configuration => PlayerConfiguration(
        title: 'MPx Player',
        bufferSize: 96 * 1024 * 1024,
        videoPerformance: AppSettingsService.videoPerformanceConfiguration,
        options: const {
          'sub-auto': 'all',
          'sub-paths': '.',
        },
      );

  Future<void> applyVideoPerformanceConfiguration(
    VideoPerformanceConfiguration configuration,
  ) async {
    _ensureNotDisposed();

    final properties = _videoPerformanceProperties(configuration);
    for (final entry in properties.entries) {
      await _player.setProperty(entry.key, entry.value);
    }
  }

  Map<String, String> _videoPerformanceProperties(
    VideoPerformanceConfiguration video,
  ) {
    final properties = <String, String>{
      if (video.hardwareDecoding != null) 'hwdec': video.hardwareDecoding!,
      if (video.decoderThreads != null)
        'vd-lavc-threads': video.decoderThreads.toString(),
      if (video.frameDropping != null) 'framedrop': video.frameDropping!,
      if (video.videoSync != null) 'video-sync': video.videoSync!,
      if (video.scaler != null) 'scale': video.scaler!,
      if (video.downScaler != null) 'dscale': video.downScaler!,
      'interpolation': video.interpolation ? 'yes' : 'no',
      if (video.temporalScaler != null) 'tscale': video.temporalScaler!,
      if (video.deinterlacing != null) 'deinterlace': video.deinterlacing!,
      if (video.gpuBackend != null) 'gpu-backend': video.gpuBackend!,
      if (video.demuxerMaxBytes != null)
        'demuxer-max-bytes': video.demuxerMaxBytes!,
      if (video.demuxerMaxBackBytes != null)
        'demuxer-max-back-bytes': video.demuxerMaxBackBytes!,
      if (video.profile != null) 'profile': video.profile!,
      if (video.cache != null) 'cache': video.cache!,
      if (video.cacheSecs != null) 'cache-secs': video.cacheSecs.toString(),
      if (video.cacheBack != null) 'cache-back': video.cacheBack!,
      if (video.hrSeek != null) 'hr-seek': video.hrSeek!,
      if (video.softwareDecodingDirectRendering != null)
        'vd-lavc-dr': video.softwareDecodingDirectRendering!,
      if (video.fastDecoding != null) 'vd-lavc-fast': video.fastDecoding!,
      if (video.openglPbo != null) 'opengl-pbo': video.openglPbo!,
      if (video.videoLatencyHacks != null)
        'video-latency-hacks': video.videoLatencyHacks!,
      if (video.gpuApi != null) 'gpu-api': video.gpuApi!,
      if (video.decoderOptions != null) 'vd-lavc-o': video.decoderOptions!,
      if (video.hwdecCodecs != null) 'hwdec-codecs': video.hwdecCodecs!,
      if (video.hrSeekFramedrop != null)
        'hr-seek-framedrop': video.hrSeekFramedrop!,
    };

    if (video.fastSeek != null && video.hrSeek == null) {
      properties['hr-seek'] = video.fastSeek == 'yes' ? 'no' : 'yes';
    }

    if (video.instantSeeking) {
      properties.addAll({
        'hr-seek': 'no',
        'hr-seek-framedrop': 'yes',
        'seek-to-file-pos': 'yes',
        'index-mode': 'both',
        'cache': 'yes',
        if (video.optimizeForLocalFiles) 'demuxer-readahead-secs': '0',
        if (video.demuxerMaxBackBytes == null) 'demuxer-max-back-bytes': '256M',
        if (video.cacheBack == null) 'cache-back': '256M',
      });
    }

    return properties;
  }

  Player get player => _player;

  @override
  Future<void> load(String path) async {
    _ensureNotDisposed();
    await _player.open(Media(path));
  }

  @override
  Future<void> play() async {
    _ensureNotDisposed();
    await _player.play();
  }

  @override
  Future<void> pause() async {
    _ensureNotDisposed();
    await _player.pause();
  }

  @override
  Future<void> seek(Duration position) async {
    _ensureNotDisposed();
    await _player.seek(position);
  }

  @override
  Future<void> setSpeed(double speed) async {
    _ensureNotDisposed();
    await _player.setRate(speed);
  }

  @override
  Future<void> setVolume(double volume) async {
    _ensureNotDisposed();
    await _player.setVolume(volume);
  }

  @override
  Future<void> enableSubtitles() async {
    _ensureNotDisposed();
    await _player.setSubtitleTrack(SubtitleTrack.auto());
    _subtitleTracksController.add(null);
  }

  @override
  Future<void> disableSubtitles() async {
    _ensureNotDisposed();
    await _player.setSubtitleTrack(SubtitleTrack.no());
    _subtitleTracksController.add(null);
  }

  @override
  List<SubtitleTrackInfo> getSubtitleTracks() {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.subtitle;
    return tracks.asMap().entries.map((entry) {
      final track = entry.value;
      return SubtitleTrackInfo(
        id: entry.key,
        title: track.title,
        language: track.language,
      );
    }).toList();
  }

  @override
  Future<void> setSubtitleTrack(int index) async {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.subtitle;
    if (index >= 0 && index < tracks.length) {
      await _player.setSubtitleTrack(tracks[index]);
      _subtitleTracksController.add(null);
    }
  }

  @override
  Future<void> loadExternalSubtitle(String path) async {
    _ensureNotDisposed();
    final fileName = path.split('/').last;
    final name = fileName.split('.').first;
    await _player.setSubtitleTrack(
      SubtitleTrack.uri(
        path,
        title: name,
        language: 'auto',
      ),
    );
    _subtitleTracksController.add(null);
  }

  @override
  List<AudioTrackInfo> getAudioTracks() {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.audio;
    return tracks.asMap().entries.map((entry) {
      final track = entry.value;
      return AudioTrackInfo(
        id: entry.key,
        title: track.title,
        language: track.language,
      );
    }).toList();
  }

  @override
  Future<void> setAudioTrack(int index) async {
    _ensureNotDisposed();
    final tracks = _player.state.tracks.audio;
    if (index >= 0 && index < tracks.length) {
      await _player.setAudioTrack(tracks[index]);
      _audioTracksController.add(null);
    }
  }

  @override
  Stream<bool> get playingStream {
    _ensureNotDisposed();
    return _player.stream.playing;
  }

  @override
  Stream<Duration> get positionStream {
    _ensureNotDisposed();
    return _player.stream.position;
  }

  @override
  Stream<Duration> get durationStream {
    _ensureNotDisposed();
    return _player.stream.duration;
  }

  @override
  Stream<bool> get bufferingStream {
    _ensureNotDisposed();
    return _player.stream.buffering;
  }

  @override
  Stream<bool> get completedStream {
    _ensureNotDisposed();
    return _player.stream.completed;
  }

  @override
  Stream<void> get audioTracksStream {
    _ensureNotDisposed();
    return _audioTracksController.stream;
  }

  @override
  Stream<void> get subtitleTracksStream {
    _ensureNotDisposed();
    return _subtitleTracksController.stream;
  }

  @override
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _audioTracksController.close();
    _subtitleTracksController.close();
    _player.dispose();
  }

  void _ensureNotDisposed() {
    if (_isDisposed) {
      throw StateError(
        'MpvPlayerRepository has been disposed and cannot be used.',
      );
    }
  }
}
