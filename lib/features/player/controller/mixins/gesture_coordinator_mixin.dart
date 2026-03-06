import 'dart:async';
import 'package:flutter/material.dart';
import '../player_state.dart';

/// Mixin for coordinating gesture priority and preventing conflicts.
/// 
/// This mixin manages gesture locking to prevent unintended interactions:
/// - Blocks long-press speed toggle during horizontal seek
/// - Blocks double-tap during seek or volume/brightness adjustment
/// - Provides gesture timeout after seek to prevent accidental taps
/// 
/// Must be applied BEFORE GestureHandlerMixin in the class declaration.
mixin GestureCoordinatorMixin on ChangeNotifier {
  PlayerState get state;
  
  bool _isSeeking = false;
  bool _isAdjustingVolume = false;
  bool _isAdjustingBrightness = false;
  Timer? _gestureLockTimer;
  
  /// Duration to lock gestures after seek completes (prevents accidental taps)
  static const _gestureLockDuration = Duration(milliseconds: 300);
  
  /// Whether gestures should be ignored temporarily
  bool get isGestureLocked => _gestureLockTimer?.isActive ?? false;
  
  /// Whether horizontal seek is in progress
  bool get isSeeking => _isSeeking;
  
  /// Whether vertical adjustment is in progress
  bool get isAdjusting => _isAdjustingVolume || _isAdjustingBrightness;
  
  /// Check if horizontal drag should be processed
  bool shouldProcessHorizontalDrag() {
    return !isAdjusting && !isGestureLocked;
  }
  
  /// Check if vertical drag should be processed
  bool shouldProcessVerticalDrag() {
    return !isSeeking && !isGestureLocked;
  }
  
  /// Check if double-tap should be processed
  bool shouldProcessDoubleTap() {
    return !isSeeking && !isAdjusting && !isGestureLocked;
  }
  
  /// Check if long-press should be processed
  bool shouldProcessLongPress() {
    return !isSeeking && !isGestureLocked;
  }
  
  /// Lock gestures for a specific duration
  void lockGesturesFor(Duration duration) {
    _gestureLockTimer?.cancel();
    _gestureLockTimer = Timer(duration, () {
      notifyListeners();
    });
  }
  
  /// Mark that seeking has started
  void startSeek() {
    _isSeeking = true;
  }
  
  /// Mark that seeking has ended and lock gestures briefly
  void endSeek() {
    _isSeeking = false;
    lockGesturesFor(_gestureLockDuration);
  }
  
  /// Mark that volume adjustment has started
  void startVolumeAdjust() {
    _isAdjustingVolume = true;
  }
  
  /// Mark that volume adjustment has ended
  void endVolumeAdjust() {
    _isAdjustingVolume = false;
  }
  
  /// Mark that brightness adjustment has started
  void startBrightnessAdjust() {
    _isAdjustingBrightness = true;
  }
  
  /// Mark that brightness adjustment has ended
  void endBrightnessAdjust() {
    _isAdjustingBrightness = false;
  }
  
  @override
  void dispose() {
    _gestureLockTimer?.cancel();
    _gestureLockTimer = null;
    super.dispose();
  }
}
