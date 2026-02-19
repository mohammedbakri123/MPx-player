/// Token to cancel thumbnail generation requests
class CancellationToken {
  bool _isCancelled = false;
  final List<void Function()> _callbacks = [];

  bool get isCancelled => _isCancelled;

  void cancel() {
    if (!_isCancelled) {
      _isCancelled = true;
      for (final callback in _callbacks) {
        callback();
      }
      _callbacks.clear();
    }
  }

  void onCancelled(void Function() callback) {
    if (_isCancelled) {
      callback();
    } else {
      _callbacks.add(callback);
    }
  }

  void dispose() {
    _callbacks.clear();
  }
}
