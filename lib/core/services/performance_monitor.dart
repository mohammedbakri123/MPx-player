import 'logger_service.dart';

/// Performance monitoring service for tracking scanner and cache performance
///
/// Provides real-time metrics and statistics for:
/// - Scan times
/// - Cache hit rates
/// - Memory usage
/// - Database operations
class PerformanceMonitor {
  static final PerformanceMonitor _instance = PerformanceMonitor._internal();
  factory PerformanceMonitor() => _instance;
  PerformanceMonitor._internal();

  // Scan metrics
  int _totalScans = 0;
  Duration _totalScanTime = Duration.zero;
  Duration _lastScanTime = Duration.zero;
  Duration _minScanTime = const Duration(milliseconds: 999999999);
  Duration _maxScanTime = Duration.zero;

  // Cache metrics
  int _cacheRequests = 0;
  int _cacheHits = 0;
  int _cacheMisses = 0;

  // Database metrics
  int _dbOperations = 0;
  Duration _totalDbTime = Duration.zero;

  // Memory tracking
  int _lastVideoCount = 0;

  // Stopwatch for current operation
  Stopwatch? _currentStopwatch;

  /// Start tracking a scan operation
  void startScan() {
    _currentStopwatch = Stopwatch()..start();
    AppLogger.i('📊 Scan started');
  }

  /// End tracking a scan operation
  void endScan(int videoCount) {
    if (_currentStopwatch == null) return;

    final elapsed = _currentStopwatch!.elapsed;
    _currentStopwatch!.stop();

    _totalScans++;
    _totalScanTime += elapsed;
    _lastScanTime = elapsed;

    if (elapsed < _minScanTime) {
      _minScanTime = elapsed;
    }
    if (elapsed > _maxScanTime) {
      _maxScanTime = elapsed;
    }

    _lastVideoCount = videoCount;

    AppLogger.i(
        '📊 Scan completed: $videoCount videos in ${_formatDuration(elapsed)}');
    _currentStopwatch = null;
  }

  /// Track a cache hit
  void trackCacheHit(String cacheType) {
    _cacheRequests++;
    _cacheHits++;
    AppLogger.d('✅ Cache hit ($cacheType)');
  }

  /// Track a cache miss
  void trackCacheMiss(String cacheType) {
    _cacheRequests++;
    _cacheMisses++;
    AppLogger.d('❌ Cache miss ($cacheType)');
  }

  /// Start tracking a database operation
  void startDbOperation() {
    _currentStopwatch = Stopwatch()..start();
  }

  /// End tracking a database operation
  void endDbOperation() {
    if (_currentStopwatch == null) return;

    final elapsed = _currentStopwatch!.elapsed;
    _currentStopwatch!.stop();

    _dbOperations++;
    _totalDbTime += elapsed;

    AppLogger.d('💾 DB operation completed in ${_formatDuration(elapsed)}');
    _currentStopwatch = null;
  }

  /// Get comprehensive performance report
  Map<String, dynamic> getPerformanceReport() {
    final avgScanTime = _totalScans > 0
        ? Duration(microseconds: _totalScanTime.inMicroseconds ~/ _totalScans)
        : Duration.zero;

    final cacheHitRate =
        _cacheRequests > 0 ? (_cacheHits / _cacheRequests * 100) : 0.0;

    final avgDbTime = _dbOperations > 0
        ? Duration(microseconds: _totalDbTime.inMicroseconds ~/ _dbOperations)
        : Duration.zero;

    return {
      'scan_metrics': {
        'total_scans': _totalScans,
        'last_scan_time': _formatDuration(_lastScanTime),
        'avg_scan_time': _formatDuration(avgScanTime),
        'min_scan_time': _formatDuration(_minScanTime),
        'max_scan_time': _formatDuration(_maxScanTime),
        'last_video_count': _lastVideoCount,
        'videos_per_second': _lastScanTime.inSeconds > 0
            ? (_lastVideoCount / _lastScanTime.inSeconds).toStringAsFixed(1)
            : 'N/A',
      },
      'cache_metrics': {
        'total_requests': _cacheRequests,
        'hits': _cacheHits,
        'misses': _cacheMisses,
        'hit_rate': '${cacheHitRate.toStringAsFixed(2)}%',
      },
      'database_metrics': {
        'total_operations': _dbOperations,
        'total_time': _formatDuration(_totalDbTime),
        'avg_operation_time': _formatDuration(avgDbTime),
      },
      'summary': {
        'performance_score':
            _calculatePerformanceScore(cacheHitRate, avgScanTime),
        'cache_efficiency': cacheHitRate > 80
            ? 'Excellent'
            : cacheHitRate > 60
                ? 'Good'
                : 'Needs Improvement',
        'scan_speed': _lastScanTime.inSeconds < 3
            ? 'Fast'
            : _lastScanTime.inSeconds < 10
                ? 'Moderate'
                : 'Slow',
      },
    };
  }

  /// Calculate overall performance score (0-100)
  int _calculatePerformanceScore(double cacheHitRate, Duration avgScanTime) {
    int score = 0;

    // Cache score (0-50 points)
    if (cacheHitRate >= 90) {
      score += 50;
    } else if (cacheHitRate >= 80) {
      score += 40;
    } else if (cacheHitRate >= 70) {
      score += 30;
    } else if (cacheHitRate >= 60) {
      score += 20;
    } else {
      score += 10;
    }

    // Scan speed score (0-50 points)
    final avgSeconds = avgScanTime.inSeconds;
    if (avgSeconds <= 2) {
      score += 50;
    } else if (avgSeconds <= 5) {
      score += 40;
    } else if (avgSeconds <= 10) {
      score += 30;
    } else if (avgSeconds <= 20) {
      score += 20;
    } else {
      score += 10;
    }

    return score;
  }

  /// Format duration for display
  String _formatDuration(Duration duration) {
    if (duration.inMilliseconds < 1000) {
      return '${duration.inMilliseconds}ms';
    } else if (duration.inSeconds < 60) {
      return '${duration.inSeconds}.${duration.inMilliseconds % 1000}s';
    } else {
      final minutes = duration.inMinutes;
      final seconds = duration.inSeconds % 60;
      return '${minutes}m ${seconds}s';
    }
  }

  /// Reset all metrics
  void reset() {
    _totalScans = 0;
    _totalScanTime = Duration.zero;
    _lastScanTime = Duration.zero;
    _minScanTime = const Duration(milliseconds: 999999999);
    _maxScanTime = Duration.zero;
    _cacheRequests = 0;
    _cacheHits = 0;
    _cacheMisses = 0;
    _dbOperations = 0;
    _totalDbTime = Duration.zero;
    _lastVideoCount = 0;
    AppLogger.i('📊 Performance metrics reset');
  }

  /// Log current performance summary
  void logSummary() {
    final report = getPerformanceReport();
    AppLogger.i('═══════════════════════════════════════════');
    AppLogger.i('📊 PERFORMANCE REPORT');
    AppLogger.i('═══════════════════════════════════════════');

    final scanMetrics = report['scan_metrics'] as Map<String, dynamic>;
    AppLogger.i('📸 Scan Metrics:');
    AppLogger.i('  Total Scans: ${scanMetrics['total_scans']}');
    AppLogger.i('  Last Scan: ${scanMetrics['last_scan_time']}');
    AppLogger.i('  Average: ${scanMetrics['avg_scan_time']}');
    AppLogger.i(
        '  Min/Max: ${scanMetrics['min_scan_time']} / ${scanMetrics['max_scan_time']}');
    AppLogger.i('  Videos/sec: ${scanMetrics['videos_per_second']}');

    final cacheMetrics = report['cache_metrics'] as Map<String, dynamic>;
    AppLogger.i('💾 Cache Metrics:');
    AppLogger.i('  Requests: ${cacheMetrics['total_requests']}');
    AppLogger.i('  Hits: ${cacheMetrics['hits']}');
    AppLogger.i('  Misses: ${cacheMetrics['misses']}');
    AppLogger.i('  Hit Rate: ${cacheMetrics['hit_rate']}');

    final summary = report['summary'] as Map<String, dynamic>;
    AppLogger.i('📈 Summary:');
    AppLogger.i('  Performance Score: ${summary['performance_score']}/100');
    AppLogger.i('  Cache Efficiency: ${summary['cache_efficiency']}');
    AppLogger.i('  Scan Speed: ${summary['scan_speed']}');

    AppLogger.i('═══════════════════════════════════════════');
  }
}

/// Global instance for easy access
final performanceMonitor = PerformanceMonitor();
