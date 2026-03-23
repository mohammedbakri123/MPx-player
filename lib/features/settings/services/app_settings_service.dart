import 'package:flutter/material.dart';
import 'package:flutter_mpv/flutter_mpv.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../player/controller/player_state.dart';

enum AppThemePreference { system, light, dark }

enum PlayerPreset { balanced, cinema, binge }

enum VideoPerformancePreset {
  powerSaver,
  balanced,
  instantSeeking,
  quality,
  smoothMotion,
  streaming,
  softwareDecoding,
}

class ExpertEngineSettings {
  final String hardwareDecoding;
  final int decoderThreads;
  final String frameDropping;
  final String videoSync;
  final String scaler;
  final String downScaler;
  final bool interpolation;
  final String temporalScaler;
  final String deinterlacing;
  final String gpuBackend;
  final String gpuApi;
  final bool optimizeForLocalFiles;
  final String cache;
  final int cacheSecs;
  final String cacheBack;
  final String demuxerMaxBytes;
  final String demuxerMaxBackBytes;
  final String hrSeek;
  final String hrSeekFramedrop;
  final String fastSeek;
  final String fastDecoding;
  final String hwdecCodecs;

  const ExpertEngineSettings({
    required this.hardwareDecoding,
    required this.decoderThreads,
    required this.frameDropping,
    required this.videoSync,
    required this.scaler,
    required this.downScaler,
    required this.interpolation,
    required this.temporalScaler,
    required this.deinterlacing,
    required this.gpuBackend,
    required this.gpuApi,
    required this.optimizeForLocalFiles,
    required this.cache,
    required this.cacheSecs,
    required this.cacheBack,
    required this.demuxerMaxBytes,
    required this.demuxerMaxBackBytes,
    required this.hrSeek,
    required this.hrSeekFramedrop,
    required this.fastSeek,
    required this.fastDecoding,
    required this.hwdecCodecs,
  });

  factory ExpertEngineSettings.defaults() =>
      ExpertEngineSettings.fromPreset(VideoPerformancePreset.instantSeeking);

  factory ExpertEngineSettings.fromPreset(VideoPerformancePreset preset) {
    final config = AppSettingsService.videoPerformancePresetToConfiguration(
      preset,
    );

    return ExpertEngineSettings(
      hardwareDecoding: config.hardwareDecoding ?? 'auto',
      decoderThreads: config.decoderThreads ?? 4,
      frameDropping: config.frameDropping ?? 'decoder',
      videoSync: config.videoSync ?? 'audio',
      scaler: config.scaler ?? 'bicubic',
      downScaler: config.downScaler ?? 'bicubic',
      interpolation: config.interpolation,
      temporalScaler: config.temporalScaler ?? 'oversample',
      deinterlacing: config.deinterlacing ?? 'auto',
      gpuBackend: config.gpuBackend ?? 'auto',
      gpuApi: config.gpuApi ?? 'auto',
      optimizeForLocalFiles: config.optimizeForLocalFiles,
      cache: config.cache ?? 'yes',
      cacheSecs: config.cacheSecs ?? 45,
      cacheBack: config.cacheBack ?? '128M',
      demuxerMaxBytes: config.demuxerMaxBytes ?? '64M',
      demuxerMaxBackBytes: config.demuxerMaxBackBytes ?? '256M',
      hrSeek: config.hrSeek ?? (config.instantSeeking ? 'no' : 'yes'),
      hrSeekFramedrop: config.hrSeekFramedrop ?? 'yes',
      fastSeek: config.fastSeek ?? (config.instantSeeking ? 'yes' : 'no'),
      fastDecoding: config.fastDecoding ?? 'no',
      hwdecCodecs: config.hwdecCodecs ?? 'all',
    );
  }

  ExpertEngineSettings copyWith({
    String? hardwareDecoding,
    int? decoderThreads,
    String? frameDropping,
    String? videoSync,
    String? scaler,
    String? downScaler,
    bool? interpolation,
    String? temporalScaler,
    String? deinterlacing,
    String? gpuBackend,
    String? gpuApi,
    bool? optimizeForLocalFiles,
    String? cache,
    int? cacheSecs,
    String? cacheBack,
    String? demuxerMaxBytes,
    String? demuxerMaxBackBytes,
    String? hrSeek,
    String? hrSeekFramedrop,
    String? fastSeek,
    String? fastDecoding,
    String? hwdecCodecs,
  }) {
    return ExpertEngineSettings(
      hardwareDecoding: hardwareDecoding ?? this.hardwareDecoding,
      decoderThreads: decoderThreads ?? this.decoderThreads,
      frameDropping: frameDropping ?? this.frameDropping,
      videoSync: videoSync ?? this.videoSync,
      scaler: scaler ?? this.scaler,
      downScaler: downScaler ?? this.downScaler,
      interpolation: interpolation ?? this.interpolation,
      temporalScaler: temporalScaler ?? this.temporalScaler,
      deinterlacing: deinterlacing ?? this.deinterlacing,
      gpuBackend: gpuBackend ?? this.gpuBackend,
      gpuApi: gpuApi ?? this.gpuApi,
      optimizeForLocalFiles:
          optimizeForLocalFiles ?? this.optimizeForLocalFiles,
      cache: cache ?? this.cache,
      cacheSecs: cacheSecs ?? this.cacheSecs,
      cacheBack: cacheBack ?? this.cacheBack,
      demuxerMaxBytes: demuxerMaxBytes ?? this.demuxerMaxBytes,
      demuxerMaxBackBytes: demuxerMaxBackBytes ?? this.demuxerMaxBackBytes,
      hrSeek: hrSeek ?? this.hrSeek,
      hrSeekFramedrop: hrSeekFramedrop ?? this.hrSeekFramedrop,
      fastSeek: fastSeek ?? this.fastSeek,
      fastDecoding: fastDecoding ?? this.fastDecoding,
      hwdecCodecs: hwdecCodecs ?? this.hwdecCodecs,
    );
  }

  VideoPerformanceConfiguration toVideoPerformanceConfiguration() {
    return VideoPerformanceConfiguration(
      hardwareDecoding: hardwareDecoding,
      decoderThreads: decoderThreads,
      frameDropping: frameDropping,
      videoSync: videoSync,
      scaler: scaler,
      downScaler: downScaler,
      interpolation: interpolation,
      temporalScaler: interpolation ? temporalScaler : null,
      deinterlacing: deinterlacing,
      gpuBackend: gpuBackend == 'auto' ? null : gpuBackend,
      gpuApi: gpuApi == 'auto' ? null : gpuApi,
      optimizeForLocalFiles: optimizeForLocalFiles,
      cache: cache,
      cacheSecs: cacheSecs,
      cacheBack: cacheBack,
      demuxerMaxBytes: demuxerMaxBytes,
      demuxerMaxBackBytes: demuxerMaxBackBytes,
      hrSeek: hrSeek,
      hrSeekFramedrop: hrSeekFramedrop,
      fastSeek: fastSeek,
      fastDecoding: fastDecoding,
      hwdecCodecs: hwdecCodecs,
    );
  }
}

class AppSettingsService {
  static const String _themePreferenceKey = 'app_theme_preference';
  static const String _playerPresetKey = 'player_preset';
  static const String _advancedOptionsEnabledKey = 'advanced_options_enabled';
  static const String _videoPerformancePresetKey = 'advanced_video_performance';
  static const String _expertEngineEnabledKey = 'expert_engine_enabled';
  static const String _autoResumePlaybackKey = 'advanced_auto_resume';
  static const String _keepScreenAwakeKey = 'advanced_keep_screen_awake';
  static const String _swipeGesturesKey = 'advanced_swipe_gestures';
  static const String _holdToBoostKey = 'advanced_hold_to_boost';
  static const String _expertHardwareDecodingKey = 'expert_hwdec';
  static const String _expertDecoderThreadsKey = 'expert_decoder_threads';
  static const String _expertFrameDroppingKey = 'expert_frame_dropping';
  static const String _expertVideoSyncKey = 'expert_video_sync';
  static const String _expertScalerKey = 'expert_scaler';
  static const String _expertDownScalerKey = 'expert_down_scaler';
  static const String _expertInterpolationKey = 'expert_interpolation';
  static const String _expertTemporalScalerKey = 'expert_temporal_scaler';
  static const String _expertDeinterlacingKey = 'expert_deinterlacing';
  static const String _expertGpuBackendKey = 'expert_gpu_backend';
  static const String _expertGpuApiKey = 'expert_gpu_api';
  static const String _expertOptimizeLocalKey = 'expert_optimize_local';
  static const String _expertCacheKey = 'expert_cache';
  static const String _expertCacheSecsKey = 'expert_cache_secs';
  static const String _expertCacheBackKey = 'expert_cache_back';
  static const String _expertDemuxerMaxBytesKey = 'expert_demuxer_max_bytes';
  static const String _expertDemuxerMaxBackBytesKey =
      'expert_demuxer_max_back_bytes';
  static const String _expertHrSeekKey = 'expert_hr_seek';
  static const String _expertHrSeekFramedropKey = 'expert_hr_seek_framedrop';
  static const String _expertFastSeekKey = 'expert_fast_seek';
  static const String _expertFastDecodingKey = 'expert_fast_decoding';
  static const String _expertHwdecCodecsKey = 'expert_hwdec_codecs';

  static late SharedPreferences _prefs;

  static Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  static AppThemePreference get themePreference {
    final value = _prefs.getString(_themePreferenceKey);
    switch (value) {
      case 'light':
        return AppThemePreference.light;
      case 'dark':
        return AppThemePreference.dark;
      default:
        return AppThemePreference.system;
    }
  }

  static ThemeMode get themeMode {
    switch (themePreference) {
      case AppThemePreference.light:
        return ThemeMode.light;
      case AppThemePreference.dark:
        return ThemeMode.dark;
      case AppThemePreference.system:
        return ThemeMode.system;
    }
  }

  static PlayerPreset get playerPreset {
    final value = _prefs.getString(_playerPresetKey);
    switch (value) {
      case 'cinema':
        return PlayerPreset.cinema;
      case 'binge':
        return PlayerPreset.binge;
      default:
        return PlayerPreset.balanced;
    }
  }

  static bool get advancedOptionsEnabled =>
      _prefs.getBool(_advancedOptionsEnabledKey) ?? false;

  static VideoPerformancePreset get videoPerformancePreset {
    final value = _prefs.getString(_videoPerformancePresetKey);
    switch (value) {
      case 'powerSaver':
        return VideoPerformancePreset.powerSaver;
      case 'balanced':
        return VideoPerformancePreset.balanced;
      case 'quality':
        return VideoPerformancePreset.quality;
      case 'smoothMotion':
        return VideoPerformancePreset.smoothMotion;
      case 'streaming':
        return VideoPerformancePreset.streaming;
      case 'softwareDecoding':
        return VideoPerformancePreset.softwareDecoding;
      default:
        return VideoPerformancePreset.instantSeeking;
    }
  }

  static bool get expertEngineEnabled =>
      _prefs.getBool(_expertEngineEnabledKey) ?? false;

  static ExpertEngineSettings get expertEngineSettings => ExpertEngineSettings(
        hardwareDecoding:
            _prefs.getString(_expertHardwareDecodingKey) ?? 'auto',
        decoderThreads: _prefs.getInt(_expertDecoderThreadsKey) ?? 4,
        frameDropping: _prefs.getString(_expertFrameDroppingKey) ?? 'decoder',
        videoSync: _prefs.getString(_expertVideoSyncKey) ?? 'audio',
        scaler: _prefs.getString(_expertScalerKey) ?? 'bicubic',
        downScaler: _prefs.getString(_expertDownScalerKey) ?? 'bicubic',
        interpolation: _prefs.getBool(_expertInterpolationKey) ?? false,
        temporalScaler:
            _prefs.getString(_expertTemporalScalerKey) ?? 'oversample',
        deinterlacing: _prefs.getString(_expertDeinterlacingKey) ?? 'auto',
        gpuBackend: _prefs.getString(_expertGpuBackendKey) ?? 'auto',
        gpuApi: _prefs.getString(_expertGpuApiKey) ?? 'auto',
        optimizeForLocalFiles: _prefs.getBool(_expertOptimizeLocalKey) ?? true,
        cache: _prefs.getString(_expertCacheKey) ?? 'yes',
        cacheSecs: _prefs.getInt(_expertCacheSecsKey) ?? 45,
        cacheBack: _prefs.getString(_expertCacheBackKey) ?? '128M',
        demuxerMaxBytes: _prefs.getString(_expertDemuxerMaxBytesKey) ?? '64M',
        demuxerMaxBackBytes:
            _prefs.getString(_expertDemuxerMaxBackBytesKey) ?? '256M',
        hrSeek: _prefs.getString(_expertHrSeekKey) ?? 'yes',
        hrSeekFramedrop: _prefs.getString(_expertHrSeekFramedropKey) ?? 'yes',
        fastSeek: _prefs.getString(_expertFastSeekKey) ?? 'no',
        fastDecoding: _prefs.getString(_expertFastDecodingKey) ?? 'no',
        hwdecCodecs: _prefs.getString(_expertHwdecCodecsKey) ?? 'all',
      );

  static bool get autoResumePlaybackSetting =>
      _prefs.getBool(_autoResumePlaybackKey) ?? true;

  static bool get keepScreenAwakeSetting =>
      _prefs.getBool(_keepScreenAwakeKey) ?? true;

  static bool get swipeGesturesSetting =>
      _prefs.getBool(_swipeGesturesKey) ?? true;

  static bool get holdToBoostSetting => _prefs.getBool(_holdToBoostKey) ?? true;

  static bool get autoResumePlayback =>
      advancedOptionsEnabled && autoResumePlaybackSetting;

  static bool get keepScreenAwake =>
      advancedOptionsEnabled && keepScreenAwakeSetting;

  static bool get swipeGesturesEnabled =>
      advancedOptionsEnabled && swipeGesturesSetting;

  static bool get holdToBoostEnabled =>
      advancedOptionsEnabled && holdToBoostSetting;

  static VideoPerformanceConfiguration videoPerformancePresetToConfiguration(
    VideoPerformancePreset preset,
  ) {
    switch (preset) {
      case VideoPerformancePreset.powerSaver:
        return VideoPerformancePresets.powerSaver;
      case VideoPerformancePreset.balanced:
        return VideoPerformancePresets.balanced;
      case VideoPerformancePreset.instantSeeking:
        return VideoPerformancePresets.instantSeeking;
      case VideoPerformancePreset.quality:
        return VideoPerformancePresets.quality;
      case VideoPerformancePreset.smoothMotion:
        return VideoPerformancePresets.smoothMotion;
      case VideoPerformancePreset.streaming:
        return VideoPerformancePresets.streaming;
      case VideoPerformancePreset.softwareDecoding:
        return VideoPerformancePresets.softwareDecoding;
    }
  }

  static VideoPerformanceConfiguration get videoPerformanceConfiguration {
    if (expertEngineEnabled) {
      return expertEngineSettings.toVideoPerformanceConfiguration();
    }

    return videoPerformancePresetToConfiguration(videoPerformancePreset);
  }

  static double get presetPlaybackSpeed {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return 1.0;
      case PlayerPreset.cinema:
        return 1.0;
      case PlayerPreset.binge:
        return 1.15;
    }
  }

  static AspectRatioMode get presetAspectRatioMode {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return AspectRatioMode.fit;
      case PlayerPreset.cinema:
        return AspectRatioMode.fill;
      case PlayerPreset.binge:
        return AspectRatioMode.fit;
    }
  }

  static RepeatMode get presetRepeatMode {
    switch (playerPreset) {
      case PlayerPreset.balanced:
        return RepeatMode.off;
      case PlayerPreset.cinema:
        return RepeatMode.one;
      case PlayerPreset.binge:
        return RepeatMode.all;
    }
  }

  static Future<bool> setThemePreference(AppThemePreference value) {
    return _prefs.setString(_themePreferenceKey, value.name);
  }

  static Future<bool> setPlayerPreset(PlayerPreset value) {
    return _prefs.setString(_playerPresetKey, value.name);
  }

  static Future<bool> setAdvancedOptionsEnabled(bool value) {
    return _prefs.setBool(_advancedOptionsEnabledKey, value);
  }

  static Future<bool> setVideoPerformancePreset(VideoPerformancePreset value) {
    return _prefs.setString(_videoPerformancePresetKey, value.name);
  }

  static Future<bool> setExpertEngineEnabled(bool value) {
    return _prefs.setBool(_expertEngineEnabledKey, value);
  }

  static Future<void> setExpertEngineSettings(
      ExpertEngineSettings value) async {
    await _prefs.setString(_expertHardwareDecodingKey, value.hardwareDecoding);
    await _prefs.setInt(_expertDecoderThreadsKey, value.decoderThreads);
    await _prefs.setString(_expertFrameDroppingKey, value.frameDropping);
    await _prefs.setString(_expertVideoSyncKey, value.videoSync);
    await _prefs.setString(_expertScalerKey, value.scaler);
    await _prefs.setString(_expertDownScalerKey, value.downScaler);
    await _prefs.setBool(_expertInterpolationKey, value.interpolation);
    await _prefs.setString(_expertTemporalScalerKey, value.temporalScaler);
    await _prefs.setString(_expertDeinterlacingKey, value.deinterlacing);
    await _prefs.setString(_expertGpuBackendKey, value.gpuBackend);
    await _prefs.setString(_expertGpuApiKey, value.gpuApi);
    await _prefs.setBool(_expertOptimizeLocalKey, value.optimizeForLocalFiles);
    await _prefs.setString(_expertCacheKey, value.cache);
    await _prefs.setInt(_expertCacheSecsKey, value.cacheSecs);
    await _prefs.setString(_expertCacheBackKey, value.cacheBack);
    await _prefs.setString(_expertDemuxerMaxBytesKey, value.demuxerMaxBytes);
    await _prefs.setString(
      _expertDemuxerMaxBackBytesKey,
      value.demuxerMaxBackBytes,
    );
    await _prefs.setString(_expertHrSeekKey, value.hrSeek);
    await _prefs.setString(_expertHrSeekFramedropKey, value.hrSeekFramedrop);
    await _prefs.setString(_expertFastSeekKey, value.fastSeek);
    await _prefs.setString(_expertFastDecodingKey, value.fastDecoding);
    await _prefs.setString(_expertHwdecCodecsKey, value.hwdecCodecs);
  }

  static Future<bool> setAutoResumePlayback(bool value) {
    return _prefs.setBool(_autoResumePlaybackKey, value);
  }

  static Future<bool> setKeepScreenAwake(bool value) {
    return _prefs.setBool(_keepScreenAwakeKey, value);
  }

  static Future<bool> setSwipeGesturesSetting(bool value) {
    return _prefs.setBool(_swipeGesturesKey, value);
  }

  static Future<bool> setHoldToBoostSetting(bool value) {
    return _prefs.setBool(_holdToBoostKey, value);
  }
}
