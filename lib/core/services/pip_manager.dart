import 'dart:io';
import 'package:flutter/material.dart';
import 'package:simple_pip_mode/simple_pip.dart';

class PipManager extends ChangeNotifier {
  static final PipManager _instance = PipManager._internal();
  factory PipManager() => _instance;
  PipManager._internal();

  SimplePip? _pip;
  bool _isInPipMode = false;

  bool get isInPipMode => _isInPipMode;

  bool get isSupported => Platform.isAndroid;

  void initialize({
    VoidCallback? onPipEntered,
    VoidCallback? onPipExited,
  }) {
    if (!isSupported) return;
    _pip = SimplePip(
      onPipEntered: () {
        _isInPipMode = true;
        notifyListeners();
      },
      onPipExited: () {
        _isInPipMode = false;
        notifyListeners();
      },
    );
  }

  Future<void> enterPipMode() async {
    if (!isSupported || _pip == null) return;
    await _pip!.enterPipMode();
  }

  Future<void> setAutoPipMode() async {
    if (!isSupported || _pip == null) return;
    await _pip!.setAutoPipMode();
  }

  @override
  void dispose() {
    _pip = null;
    _isInPipMode = false;
    super.dispose();
  }
}
