import 'dart:io';
import 'dart:ui';
import '../../../core/services/logger_service.dart';

class ThumbnailConfig {
  static final ThumbnailConfig _instance = ThumbnailConfig._internal();
  factory ThumbnailConfig() => _instance;
  ThumbnailConfig._internal();

  late int _maxWidth;
  late int _maxHeight;
  late int _quality;
  late int _workerCount;
  late int _batchSize;
  late int _memoryCacheSize;

  bool _initialized = false;

  Future<void> initialize() async {
    if (_initialized) return;

    double devicePixelRatio = 1.0;
    try {
      final displays = PlatformDispatcher.instance.displays;
      if (displays.isNotEmpty) {
        devicePixelRatio = displays.first.devicePixelRatio;
      }
    } catch (e) {
      // Use default
    }

    final physicalMemory = await _getDeviceMemory();
    final isLowEnd = _isLowEndDevice(physicalMemory);

    if (isLowEnd) {
      _maxWidth = 240;
      _maxHeight = 160;
      _quality = 50;
      _workerCount = 2;
      _batchSize = 5;
      _memoryCacheSize = 30 * 1024 * 1024;
    } else if (devicePixelRatio >= 3.0) {
      _maxWidth = 480;
      _maxHeight = 320;
      _quality = 70;
      _workerCount = 4;
      _batchSize = 15;
      _memoryCacheSize = 80 * 1024 * 1024;
    } else if (devicePixelRatio >= 2.0) {
      _maxWidth = 360;
      _maxHeight = 240;
      _quality = 65;
      _workerCount = 3;
      _batchSize = 10;
      _memoryCacheSize = 60 * 1024 * 1024;
    } else {
      _maxWidth = 300;
      _maxHeight = 200;
      _quality = 60;
      _workerCount = 3;
      _batchSize = 10;
      _memoryCacheSize = 50 * 1024 * 1024;
    }

    _initialized = true;

    AppLogger.i('''
ThumbnailConfig initialized:
  - Device pixel ratio: $devicePixelRatio
  - Low-end device: $isLowEnd
  - Thumbnail size: ${_maxWidth}x$_maxHeight
  - Quality: $_quality
  - Workers: $_workerCount
  - Batch size: $_batchSize
  - Memory cache: ${_memoryCacheSize ~/ (1024 * 1024)}MB
''');
  }

  Future<int> _getDeviceMemory() async {
    try {
      if (Platform.isAndroid) {
        final result = await Process.run('cat', ['/proc/meminfo']);
        final output = result.stdout.toString();
        final match = RegExp(r'MemTotal:\s+(\d+)').firstMatch(output);
        if (match != null) {
          return int.parse(match.group(1)!) * 1024;
        }
      }
    } catch (e) {
      // Ignore
    }
    return 4 * 1024 * 1024 * 1024;
  }

  bool _isLowEndDevice(int memory) {
    return memory < 3 * 1024 * 1024 * 1024;
  }

  int get maxWidth {
    _ensureInitialized();
    return _maxWidth;
  }

  int get maxHeight {
    _ensureInitialized();
    return _maxHeight;
  }

  int get quality {
    _ensureInitialized();
    return _quality;
  }

  int get workerCount {
    _ensureInitialized();
    return _workerCount;
  }

  int get batchSize {
    _ensureInitialized();
    return _batchSize;
  }

  int get memoryCacheSize {
    _ensureInitialized();
    return _memoryCacheSize;
  }

  void _ensureInitialized() {
    if (!_initialized) {
      _maxWidth = 300;
      _maxHeight = 200;
      _quality = 60;
      _workerCount = 3;
      _batchSize = 10;
      _memoryCacheSize = 50 * 1024 * 1024;
    }
  }
}
