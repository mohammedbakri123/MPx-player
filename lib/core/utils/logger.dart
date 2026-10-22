import 'package:flutter/foundation.dart';

enum LogLevel { debug, info, warn, error }

abstract final class AppLogger {
  static const String _prefix = 'MPx';

  static void d(String tag, String message) => _log(LogLevel.debug, tag, message);

  static void i(String tag, String message) => _log(LogLevel.info, tag, message);

  static void w(String tag, String message) => _log(LogLevel.warn, tag, message);

  static void e(
    String tag,
    String message, [
    Object? error,
    StackTrace? stackTrace,
  ]) =>
      _log(LogLevel.error, tag, message, error, stackTrace);

  static void _log(
    LogLevel level,
    String tag,
    String message, [
    Object? error,
    StackTrace? stackTrace,
  ]) {
    if (kReleaseMode) return;
    final String suffix = error != null ? ' — $error' : '';
    debugPrint('[$_prefix/$level]\t$tag: $message$suffix');
    if (stackTrace != null) {
      debugPrint(stackTrace.toString());
    }
  }
}
