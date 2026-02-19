import 'dart:async';
import 'dart:collection';

/// Debouncer for preventing rapid repeated actions
/// 
/// Useful for preventing multiple rapid refreshes or searches.
class Debouncer {
  final Duration delay;
  Timer? _timer;

  Debouncer({required this.delay});

  /// Call the action after the delay has passed
  /// If called again before delay expires, resets the timer
  void call(void Function() action) {
    _timer?.cancel();
    _timer = Timer(delay, action);
  }

  /// Execute immediately and cancel any pending action
  void executeNow(void Function() action) {
    _timer?.cancel();
    action();
  }

  /// Cancel any pending action
  void cancel() {
    _timer?.cancel();
  }

  /// Whether there's a pending action
  bool get hasPending => _timer?.isActive ?? false;
}

/// Throttler for limiting action frequency
/// 
/// Ensures an action is called at most once per interval.
class Throttler {
  final Duration interval;
  Timer? _timer;
  bool _pending = false;
  void Function()? _pendingAction;

  Throttler({required this.interval});

  /// Call the action, but throttle to once per interval
  void call(void Function() action) {
    if (_timer == null || !_timer!.isActive) {
      // Not throttled, execute immediately
      action();
      _timer = Timer(interval, _onTimerComplete);
    } else {
      // Throttled, queue for later
      _pendingAction = action;
      _pending = true;
    }
  }

  void _onTimerComplete() {
    if (_pending && _pendingAction != null) {
      final action = _pendingAction!;
      _pending = false;
      _pendingAction = null;
      action();
      _timer = Timer(interval, _onTimerComplete);
    }
  }

  /// Execute immediately, bypassing throttle
  void executeNow(void Function() action) {
    _timer?.cancel();
    _timer = null;
    _pending = false;
    _pendingAction = null;
    action();
  }

  /// Cancel any pending action
  void cancel() {
    _timer?.cancel();
    _pending = false;
    _pendingAction = null;
  }
}

/// Rate limiter for controlling concurrent operations
class RateLimiter {
  final int maxConcurrent;
  final Queue<_Task> _queue = Queue();
  int _running = 0;

  RateLimiter({required this.maxConcurrent});

  /// Add a task to be executed when capacity is available
  Future<T> add<T>(Future<T> Function() task) async {
    if (_running < maxConcurrent) {
      return _executeTask(task);
    }

    // Queue the task
    final completer = Completer<T>();
    _queue.add(_Task(task: task, completer: completer));
    return completer.future;
  }

  Future<T> _executeTask<T>(Future<T> Function() task) async {
    _running++;
    try {
      final result = await task();
      return result;
    } finally {
      _running--;
      _processQueue();
    }
  }

  void _processQueue() {
    if (_queue.isNotEmpty && _running < maxConcurrent) {
      final task = _queue.removeFirst();
      task.execute(this);
    }
  }

  /// Get current stats
  Map<String, int> get stats => {
    'running': _running,
    'queued': _queue.length,
  };
}

class _Task<T> {
  final Future<T> Function() task;
  final Completer<T> completer;

  _Task({required this.task, required this.completer});

  Future<void> execute(RateLimiter limiter) async {
    try {
      final result = await task();
      completer.complete(result);
    } catch (e) {
      completer.completeError(e);
    }
  }
}

/// Cooldown manager for preventing actions too soon
class CooldownManager {
  final Duration cooldown;
  DateTime? _lastAction;

  CooldownManager({required this.cooldown});

  /// Try to execute action, returns false if on cooldown
  bool tryExecute(void Function() action) {
    final now = DateTime.now();
    if (_lastAction == null || now.difference(_lastAction!) >= cooldown) {
      action();
      _lastAction = now;
      return true;
    }
    return false;
  }

  /// Execute anyway, ignoring cooldown
  void forceExecute(void Function() action) {
    action();
    _lastAction = DateTime.now();
  }

  /// Get remaining cooldown time
  Duration get remainingCooldown {
    if (_lastAction == null) return Duration.zero;
    final elapsed = DateTime.now().difference(_lastAction!);
    if (elapsed >= cooldown) return Duration.zero;
    return cooldown - elapsed;
  }

  /// Whether action is on cooldown
  bool get isOnCooldown => remainingCooldown > Duration.zero;
}
