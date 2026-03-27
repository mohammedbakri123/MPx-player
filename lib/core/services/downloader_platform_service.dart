import 'dart:async';

import 'package:flutter/services.dart';

class DownloaderPlatformService {
  DownloaderPlatformService._();

  static final DownloaderPlatformService instance =
      DownloaderPlatformService._();

  static const MethodChannel _methodChannel =
      MethodChannel('mpx/downloader/methods');
  static const EventChannel _eventChannel =
      EventChannel('mpx/downloader/events');

  Stream<Map<String, dynamic>>? _events;

  Stream<Map<String, dynamic>> get events {
    return _events ??= _eventChannel
        .receiveBroadcastStream()
        .map((event) => Map<String, dynamic>.from(event as Map));
  }

  Stream<String> get sharedUrlEvents {
    return events
        .where((event) => event['event'] == 'shared_url')
        .map((event) => event['url'] as String)
        .where((url) => url.isNotEmpty);
  }

  Future<Map<String, dynamic>> getStatus() async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'getDownloaderStatus',
    );
    return result ?? <String, dynamic>{};
  }

  Future<Map<String, dynamic>> checkForUpdates({
    bool installIfAvailable = true,
  }) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'checkForUpdates',
      <String, dynamic>{'installIfAvailable': installIfAvailable},
    );
    return result ?? <String, dynamic>{};
  }

  Future<String?> consumeSharedUrl() async {
    return _methodChannel.invokeMethod<String>('consumeSharedUrl');
  }

  Future<String?> exportDownload(String sourcePath) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'exportDownload',
      <String, dynamic>{'sourcePath': sourcePath},
    );
    return result?['path'] as String?;
  }

  Future<Map<String, dynamic>> ensureBinariesAvailable({
    String? ytDlpPath,
    String? ffmpegPath,
    String? version,
  }) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'ensureBinariesAvailable',
      <String, dynamic>{
        'ytDlpPath': ytDlpPath,
        'ffmpegPath': ffmpegPath,
        'version': version,
      },
    );
    return result ?? <String, dynamic>{};
  }

  Future<Map<String, dynamic>> fetchVideoInfo({
    required String ytDlpPath,
    required String url,
    String? cookiesPath,
  }) async {
    final result = await _methodChannel.invokeMapMethod<String, dynamic>(
      'fetchVideoInfo',
      <String, dynamic>{
        'ytDlpPath': ytDlpPath,
        'url': url,
        'cookiesPath': cookiesPath,
      },
    );
    return result ?? <String, dynamic>{};
  }

  Future<void> startDownload({
    required String taskId,
    required String ytDlpPath,
    String? ffmpegPath,
    String? cookiesPath,
    required String url,
    required String outputPath,
    required String formatSelector,
  }) {
    return _methodChannel.invokeMethod<void>('startDownload', <String, dynamic>{
      'taskId': taskId,
      'ytDlpPath': ytDlpPath,
      'ffmpegPath': ffmpegPath,
      'cookiesPath': cookiesPath,
      'url': url,
      'outputPath': outputPath,
      'formatSelector': formatSelector,
    });
  }

  Future<void> cancelDownload(String taskId) {
    return _methodChannel.invokeMethod<void>(
      'cancelDownload',
      <String, dynamic>{'taskId': taskId},
    );
  }
}
