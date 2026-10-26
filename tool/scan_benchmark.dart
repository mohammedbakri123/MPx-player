import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:path/path.dart' as p;

const Set<String> _videoExtensions = <String>{
  '.mp4',
  '.mkv',
  '.webm',
  '.avi',
  '.mov',
  '.m4v',
  '.ts',
  '.m2ts',
  '.mpg',
  '.mpeg',
  '.flv',
  '.wmv',
  '.3gp',
  '.ogv',
};

const Set<String> _audioExtensions = <String>{
  '.mp3',
  '.flac',
  '.m4a',
  '.aac',
  '.ogg',
  '.opus',
  '.wav',
  '.wma',
};

final Set<String> _mediaExtensions = <String>{
  ..._videoExtensions,
  ..._audioExtensions,
};

const List<String> _defaultExcludes = <String>[
  '.cache',
  '.local',
  '.pub-cache',
  'Projects',
  '.git',
];

class _Entry {
  const _Entry(this.path, this.sizeBytes, this.modifiedMs);

  final String path;
  final int sizeBytes;
  final int modifiedMs;
}

class _Options {
  const _Options({
    required this.roots,
    required this.excludes,
    required this.runs,
    required this.workers,
    required this.sniffTs,
  });

  final List<String> roots;
  final List<String> excludes;
  final int runs;
  final List<int> workers;
  final bool sniffTs;
}

class _Result {
  const _Result(this.name, this.samplesUs, this.count, this.fingerprint);

  final String name;
  final List<int> samplesUs;
  final int count;
  final int fingerprint;

  int get minUs => samplesUs.reduce(math.min);

  double get medianMs {
    final List<int> sorted = <int>[...samplesUs]..sort();
    final int mid = sorted.length ~/ 2;
    if (sorted.length.isOdd) {
      return sorted[mid] / 1000;
    }
    return (sorted[mid - 1] + sorted[mid]) / 2000;
  }
}

Future<void> main(List<String> args) async {
  final _Options options = _parseOptions(args);
  if (options.roots.isEmpty) {
    stderr.writeln('No scan roots found. Pass --root <path>.');
    exitCode = 2;
    return;
  }

  stdout.writeln('roots: ${options.roots.join(', ')}');
  stdout.writeln('runs: ${options.runs} (plus one warmup)');
  stdout.writeln('excludes: ${options.excludes.join(', ')}');
  stdout.writeln('cores: ${Platform.numberOfProcessors}');
  stdout.writeln('');

  final List<_Result> results = <_Result>[];

  results.add(await _measure(
    'sync-stat (current)',
    () => _walkSyncStat(options.roots, options.excludes),
    options.runs,
  ));
  results.add(await _measure(
    'async-stat single',
    () => _walkAsyncStat(options.roots, options.excludes),
    options.runs,
  ));

  for (final int workers in options.workers) {
    results.add(await _measure(
      'isolate-pool x$workers',
      () => _walkPool(options.roots, workers, options.excludes),
      options.runs,
    ));
  }

  if (await _commandExists('fd')) {
    results.add(await _measure(
      'fd x${options.workers.last}',
      () => _walkFd(options.roots, options.workers.last),
      options.runs,
    ));
  } else {
    stdout.writeln('fd not found, skipping');
  }

  if (await _commandExists('find')) {
    results.add(await _measure(
      'find',
      () => _walkFind(options.roots, options.excludes),
      options.runs,
    ));
  } else {
    stdout.writeln('find not found, skipping');
  }

  stdout.writeln('');
  stdout.writeln('variant                    count    min ms   median ms   paths');
  stdout.writeln('---------------------------------------------------------------');
  for (final _Result result in results) {
    stdout.writeln(
      '${result.name.padRight(26)}'
      '${result.count.toString().padLeft(6)}'
      '${(result.minUs / 1000).toStringAsFixed(1).padLeft(10)}'
      '${result.medianMs.toStringAsFixed(1).padLeft(12)}'
      '  ${result.fingerprint.toRadixString(16)}',
    );
  }

  if (options.sniffTs) {
    final List<_Entry> entries =
        await _walkAsyncStat(options.roots, options.excludes);
    final List<String> tsFiles = entries
        .map((_Entry entry) => entry.path)
        .where((String path) => path.toLowerCase().endsWith('.ts'))
        .toList(growable: false);
    final Stopwatch stopwatch = Stopwatch()..start();
    int realTs = 0;
    for (final String path in tsFiles) {
      if (await _isMpegTs(path)) {
        realTs++;
      }
    }
    stopwatch.stop();
    stdout.writeln('');
    stdout.writeln(
      '.ts sniff: ${tsFiles.length} candidates, $realTs real MPEG-TS, '
      '${(stopwatch.elapsedMicroseconds / 1000).toStringAsFixed(1)} ms',
    );
  }
}

_Options _parseOptions(List<String> args) {
  final List<String> roots = <String>[];
  final List<String> excludes = <String>[];
  final List<int> workers = <int>[];
  int runs = 3;
  bool sniffTs = false;
  bool home = false;

  for (int i = 0; i < args.length; i++) {
    final String arg = args[i];
    switch (arg) {
      case '--root':
        roots.add(args[++i]);
      case '--exclude':
        excludes.add(args[++i]);
      case '--workers':
        workers.addAll(
          args[++i].split(',').map(int.parse).where((int value) => value > 0),
        );
      case '--runs':
        runs = int.parse(args[++i]);
      case '--sniff-ts':
        sniffTs = true;
      case '--home':
        home = true;
      default:
        stderr.writeln('Unknown argument: $arg');
        exit(2);
    }
  }

  if (home) {
    roots.add(Platform.environment['HOME'] ?? '');
  }
  if (excludes.isEmpty) {
    excludes.addAll(_defaultExcludes);
  }
  if (workers.isEmpty) {
    final int cores = Platform.numberOfProcessors;
    workers.addAll(<int>{
      2,
      4,
      math.min(8, cores),
    }.where((int value) => value <= cores && value > 0));
  }
  if (roots.isEmpty) {
    final String homeDir = Platform.environment['HOME'] ?? '';
    for (final String name in <String>['Videos', 'Music']) {
      final Directory dir = Directory(p.join(homeDir, name));
      if (dir.existsSync()) {
        roots.add(dir.path);
      }
    }
  }

  return _Options(
    roots: roots.where((String path) => path.isNotEmpty).toList(growable: false),
    excludes: excludes.toList(growable: false),
    runs: runs,
    workers: workers.toList(growable: false),
    sniffTs: sniffTs,
  );
}

Future<_Result> _measure(
  String name,
  Future<List<_Entry>> Function() run,
  int runs,
) async {
  stdout.writeln('running $name...');
  final List<int> samples = <int>[];
  List<_Entry> last = const <_Entry>[];
  for (int i = 0; i <= runs; i++) {
    final Stopwatch stopwatch = Stopwatch()..start();
    last = await run();
    stopwatch.stop();
    if (i > 0) {
      samples.add(stopwatch.elapsedMicroseconds);
    }
  }
  return _Result(name, samples, last.length, _fingerprint(last));
}

int _fingerprint(List<_Entry> entries) {
  final List<String> paths =
      entries.map((_Entry entry) => entry.path).toList(growable: false)..sort();
  int hash = 0xcbf29ce484222325;
  for (final String path in paths) {
    for (final int unit in path.codeUnits) {
      hash ^= unit;
      hash = (hash * 0x100000001b3) & 0x7FFFFFFFFFFFFFFF;
    }
  }
  return hash;
}

bool _isMedia(String path) =>
    _mediaExtensions.contains(p.extension(path).toLowerCase());

bool _isExcluded(String path, List<String> excludes) {
  final List<String> parts = p.split(path);
  for (final String part in parts) {
    if (excludes.contains(part)) {
      return true;
    }
  }
  return false;
}

Future<List<_Entry>> _walkSyncStat(
  List<String> roots,
  List<String> excludes,
) async {
  final List<_Entry> entries = <_Entry>[];
  final List<String> stack = <String>[];
  for (final String root in roots) {
    try {
      await for (final FileSystemEntity entity
          in Directory(root).list(followLinks: false)) {
        if (_isExcluded(entity.path, excludes)) {
          continue;
        }
        if (entity is Directory) {
          stack.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          _addSyncEntry(entries, entity);
        }
      }
    } on FileSystemException {
      continue;
    }
  }
  while (stack.isNotEmpty) {
    final String current = stack.removeLast();
    try {
      await for (final FileSystemEntity entity
          in Directory(current).list(followLinks: false)) {
        if (_isExcluded(entity.path, excludes)) {
          continue;
        }
        if (entity is Directory) {
          stack.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          _addSyncEntry(entries, entity);
        }
      }
    } on FileSystemException {
      continue;
    }
  }
  return entries;
}

void _addSyncEntry(List<_Entry> entries, File file) {
  try {
    final FileStat stat = file.statSync();
    entries.add(
      _Entry(
        file.path,
        stat.size,
        stat.modified.millisecondsSinceEpoch,
      ),
    );
  } on FileSystemException {
    return;
  }
}

Future<List<_Entry>> _walkAsyncStat(
  List<String> roots,
  List<String> excludes,
) async {
  final List<_Entry> entries = <_Entry>[];
  final List<String> stack = <String>[];
  for (final String root in roots) {
    try {
      await for (final FileSystemEntity entity
          in Directory(root).list(followLinks: false)) {
        if (_isExcluded(entity.path, excludes)) {
          continue;
        }
        if (entity is Directory) {
          stack.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          await _addAsyncEntry(entries, entity);
        }
      }
    } on FileSystemException {
      continue;
    }
  }
  while (stack.isNotEmpty) {
    final String current = stack.removeLast();
    try {
      await for (final FileSystemEntity entity
          in Directory(current).list(followLinks: false)) {
        if (_isExcluded(entity.path, excludes)) {
          continue;
        }
        if (entity is Directory) {
          stack.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          await _addAsyncEntry(entries, entity);
        }
      }
    } on FileSystemException {
      continue;
    }
  }
  return entries;
}

Future<void> _addAsyncEntry(List<_Entry> entries, File file) async {
  try {
    final FileStat stat = await file.stat();
    entries.add(
      _Entry(
        file.path,
        stat.size,
        stat.modified.millisecondsSinceEpoch,
      ),
    );
  } on FileSystemException {
    return;
  }
}

Future<List<_Entry>> _walkPool(
  List<String> roots,
  int workers,
  List<String> excludes,
) async {
  final List<String> seedDirs = <String>[];
  final List<String> seedFiles = <String>[];
  for (final String root in roots) {
    try {
      await for (final FileSystemEntity entity
          in Directory(root).list(followLinks: false)) {
        if (_isExcluded(entity.path, excludes)) {
          continue;
        }
        if (entity is Directory) {
          seedDirs.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          seedFiles.add(entity.path);
        }
      }
    } on FileSystemException {
      continue;
    }
  }

  final int workerCount =
      math.min(workers, math.max(1, seedDirs.length + seedFiles.length));
  final List<List<String>> dirChunks =
      List<List<String>>.generate(workerCount, (_) => <String>[]);
  final List<List<String>> fileChunks =
      List<List<String>>.generate(workerCount, (_) => <String>[]);
  for (int i = 0; i < seedDirs.length; i++) {
    dirChunks[i % workerCount].add(seedDirs[i]);
  }
  for (int i = 0; i < seedFiles.length; i++) {
    fileChunks[i % workerCount].add(seedFiles[i]);
  }

  final List<List<List<Object>>> results = await Future.wait(
    List<Future<List<List<Object>>>>.generate(
      workerCount,
      (int index) => Isolate.run(
        () => _walkSeeds(dirChunks[index], fileChunks[index], excludes),
      ),
    ),
  );

  final List<_Entry> entries = <_Entry>[];
  for (final List<List<Object>> chunkResult in results) {
    for (final List<Object> row in chunkResult) {
      entries.add(_Entry(row[0] as String, row[1] as int, row[2] as int));
    }
  }
  return entries;
}

Future<List<List<Object>>> _walkSeeds(
  List<String> seedDirs,
  List<String> seedFiles,
  List<String> excludes,
) async {
  final List<List<Object>> entries = <List<Object>>[];
  for (final String path in seedFiles) {
    try {
      final FileStat stat = await FileStat.stat(path);
      entries.add(<Object>[
        path,
        stat.size,
        stat.modified.millisecondsSinceEpoch,
      ]);
    } on FileSystemException {
      continue;
    }
  }
  final List<String> stack = <String>[...seedDirs];
  while (stack.isNotEmpty) {
    final String current = stack.removeLast();
    if (_isExcluded(current, excludes)) {
      continue;
    }
    try {
      await for (final FileSystemEntity entity
          in Directory(current).list(followLinks: false)) {
        if (entity is Directory) {
          stack.add(entity.path);
        } else if (entity is File && _isMedia(entity.path)) {
          try {
            final FileStat stat = await entity.stat();
            entries.add(<Object>[
              entity.path,
              stat.size,
              stat.modified.millisecondsSinceEpoch,
            ]);
          } on FileSystemException {
            continue;
          }
        }
      }
    } on FileSystemException {
      continue;
    }
  }
  return entries;
}

Future<List<_Entry>> _walkFd(List<String> roots, int workers) async {
  final List<String> arguments = <String>[
    '-HI',
    '-0',
    '-t',
    'f',
    '-j',
    workers.toString(),
    for (final String extension in _mediaExtensions) ...<String>[
      '-e',
      extension.substring(1),
    ],
    for (final String exclude in _defaultExcludes) ...<String>[
      '--exclude',
      exclude,
    ],
    '.',
    ...roots,
  ];
  final ProcessResult result = await Process.run('fd', arguments);
  if (result.exitCode != 0) {
    throw ProcessException(
      'fd',
      arguments,
      result.stderr.toString(),
      result.exitCode,
    );
  }
  final List<_Entry> entries = <_Entry>[];
  for (final String path in (result.stdout as String).split('\u0000')) {
    if (path.isEmpty) {
      continue;
    }
    entries.add(_Entry(path, 0, 0));
  }
  return entries;
}

Future<List<_Entry>> _walkFind(List<String> roots, List<String> excludes) async {
  final List<_Entry> entries = <_Entry>[];
  for (final String root in roots) {
    final List<String> arguments = <String>[
      root,
      '-xdev',
      '(',
      for (int i = 0; i < excludes.length; i++) ...<String>[
        if (i > 0) '-o',
        '-name',
        excludes[i],
      ],
      ')',
      '-prune',
      '-o',
      '-type',
      'f',
      '(',
      for (int i = 0; i < _mediaExtensions.length; i++) ...<String>[
        if (i > 0) '-o',
        '-iname',
        '*${_mediaExtensions.elementAt(i)}',
      ],
      ')',
      '-printf',
      '%p\\t%s\\t%T@\\n',
    ];
    final ProcessResult result = await Process.run('find', arguments);
    if (result.exitCode != 0 && (result.stdout as String).isEmpty) {
      continue;
    }
    for (final String line in (result.stdout as String).split('\n')) {
      if (line.isEmpty) {
        continue;
      }
      final List<String> fields = line.split('\t');
      if (fields.length != 3) {
        continue;
      }
      entries.add(
        _Entry(
          fields[0],
          int.tryParse(fields[1]) ?? 0,
          (double.tryParse(fields[2]) ?? 0).round() * 1000,
        ),
      );
    }
  }
  return entries;
}

Future<bool> _commandExists(String command) async {
  try {
    final ProcessResult result =
        await Process.run('which', <String>[command]);
    return result.exitCode == 0;
  } on ProcessException {
    return false;
  }
}

Future<bool> _isMpegTs(String path) async {
  final File file = File(path);
  RandomAccessFile? handle;
  try {
    handle = await file.open();
    final int length = await handle.length();
    if (length < 377) {
      return false;
    }
    final Uint8List bytes = await handle.read(377);
    return bytes[0] == 0x47 && bytes[188] == 0x47 && bytes[376] == 0x47;
  } on FileSystemException {
    return false;
  } finally {
    await handle?.close();
  }
}
