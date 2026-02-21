import 'dart:collection';

/// LRU (Least Recently Used) Cache implementation with size limit
class LRUCache<K, V> {
  final int maxSize;
  final LinkedHashMap<K, V> _cache = LinkedHashMap<K, V>();

  LRUCache(this.maxSize) {
    assert(maxSize > 0, 'Cache size must be greater than 0');
  }

  /// Get value from cache and mark as recently used
  V? get(K key) {
    final value = _cache.remove(key);
    if (value != null) {
      _cache[key] = value; // Move to end (most recently used)
      return value;
    }
    return null;
  }

  /// Add value to cache
  void put(K key, V value) {
    if (_cache.containsKey(key)) {
      _cache.remove(key);
    } else if (_cache.length >= maxSize) {
      // Remove least recently used (first item)
      _cache.remove(_cache.keys.first);
    }
    _cache[key] = value;
  }

  /// Check if key exists in cache
  bool containsKey(K key) => _cache.containsKey(key);

  /// Get current cache size
  int get length => _cache.length;

  /// Clear all cached items
  void clear() => _cache.clear();

  /// Get all keys
  Iterable<K> get keys => _cache.keys;

  /// Remove specific key
  V? remove(K key) => _cache.remove(key);

  /// Get all values
  Iterable<V> get values => _cache.values;

  /// Find key by value and remove it
  K? findKeyByValue(V value) {
    for (final entry in _cache.entries) {
      if (entry.value == value) {
        return entry.key;
      }
    }
    return null;
  }

  /// Remove entry by value
  void removeByValue(V value) {
    final key = findKeyByValue(value);
    if (key != null) {
      _cache.remove(key);
    }
  }
}
