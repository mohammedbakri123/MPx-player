import 'dart:developer' as developer;

/// A centralized logging service for the application.
///
/// This service provides different levels of logging (info, warning, error)
/// and can be easily replaced with a more sophisticated logging solution
/// if needed in the future.
class AppLogger {
  /// Logs an informational message.
  static void i(String message, [Object? error, StackTrace? stackTrace]) {
    _log('INFO', message, error, stackTrace);
  }

  /// Logs a warning message.
  static void w(String message, [Object? error, StackTrace? stackTrace]) {
    _log('WARNING', message, error, stackTrace);
  }

  /// Logs an error message.
  static void e(String message, [Object? error, StackTrace? stackTrace]) {
    _log('ERROR', message, error, stackTrace);
  }

  /// Internal method to handle the actual logging.
  static void _log(String level, String message, [Object? error, StackTrace? stackTrace]) {
    final timestamp = DateTime.now().toString().split('.')[0]; // Remove milliseconds
    final logMessage = '[$level] [$timestamp] $message';
    
    // Log to the console
    developer.log(
      logMessage,
      error: error,
      stackTrace: stackTrace,
      name: 'MPxPlayer',
    );
  }
}