import 'package:flutter/material.dart';
import 'package:mpx/core/services/permission_service.dart';
import 'main_screen.dart';
import 'permission_request_screen.dart';

class PermissionWrapper extends StatefulWidget {
  const PermissionWrapper({super.key});

  @override
  State<PermissionWrapper> createState() => _PermissionWrapperState();
}

class _PermissionWrapperState extends State<PermissionWrapper> {
  bool _isLoading = true;
  bool _hasPermission = false;

  @override
  void initState() {
    super.initState();
    _checkPermission();
  }

  Future<void> _checkPermission() async {
    final hasPermission = await PermissionService.checkStoragePermission();
    setState(() {
      _hasPermission = hasPermission;
      _isLoading = false;
    });
  }

  Future<void> _requestPermission() async {
    setState(() => _isLoading = true);
    final granted = await PermissionService.requestStoragePermissions();
    setState(() {
      _hasPermission = granted;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    if (!_hasPermission) {
      return PermissionRequestScreen(
        onRequestPermission: _requestPermission,
      );
    }

    return const MainScreen();
  }
}
