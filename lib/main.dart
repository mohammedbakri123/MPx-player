import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:provider/provider.dart';
import 'features/library/presentation/screens/home_screen.dart' as lib_screens;
import 'features/library/controller/library_controller.dart';
import 'features/library/data/datasources/local_video_scanner.dart';
import 'features/favorites/presentation/screens/favorites_screen.dart';
import 'features/settings/presentation/screens/settings_screen.dart';
import 'core/services/permission_service.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();

  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ),
  );

  runApp(const MPxPlayer());
}

class MPxPlayer extends StatelessWidget {
  const MPxPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    // Provide LibraryController at the app level so it persists across navigation
    return ChangeNotifierProvider(
      create: (_) => LibraryController(VideoScanner()),
      child: MaterialApp(
        title: 'MPx Player',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF6366F1),
            brightness: Brightness.light,
          ),
          useMaterial3: true,
          scaffoldBackgroundColor: const Color(0xFFF8FAFC),
        ),
        home: const PermissionWrapper(),
      ),
    );
  }
}

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

class PermissionRequestScreen extends StatefulWidget {
  final VoidCallback onRequestPermission;

  const PermissionRequestScreen({
    super.key,
    required this.onRequestPermission,
  });

  @override
  State<PermissionRequestScreen> createState() =>
      _PermissionRequestScreenState();
}

class _PermissionRequestScreenState extends State<PermissionRequestScreen> {
  Map<String, PermissionStatus> _statuses = {};
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    final statuses = await PermissionService.getPermissionStatus();
    setState(() {
      _statuses = statuses;
    });
  }

  Future<void> _requestPermission() async {
    setState(() => _isLoading = true);
    widget.onRequestPermission();
    await Future.delayed(const Duration(milliseconds: 500));
    await _checkStatus();
    setState(() => _isLoading = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(30),
                ),
                child: const Icon(
                  Icons.folder_open,
                  size: 60,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 40),
              const Text(
                'Storage Access Required',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'MPx Player needs access to your videos to play them.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 16,
                  color: Colors.grey.shade600,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 24),

              // Permission Status
              if (_statuses.isNotEmpty) ...[
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade100,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    children: [
                      _buildPermissionRow('Photos', _statuses['photos']),
                      const SizedBox(height: 8),
                      _buildPermissionRow('Videos', _statuses['videos']),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
              ],

              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _requestPermission,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF6366F1),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            color: Colors.white,
                            strokeWidth: 2,
                          ),
                        )
                      : const Text(
                          'Grant Access',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                ),
              ),
              const SizedBox(height: 16),
              TextButton(
                onPressed: () async {
                  await PermissionService.openSettings();
                },
                child: const Text('Open Settings'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPermissionRow(String name, PermissionStatus? status) {
    IconData icon;
    Color color;
    String text;

    if (status == null) {
      icon = Icons.help_outline;
      color = Colors.grey;
      text = 'Unknown';
    } else if (status.isGranted) {
      icon = Icons.check_circle;
      color = Colors.green;
      text = 'Granted';
    } else if (status.isDenied) {
      icon = Icons.cancel;
      color = Colors.orange;
      text = 'Denied';
    } else if (status.isPermanentlyDenied) {
      icon = Icons.block;
      color = Colors.red;
      text = 'Permanently Denied';
    } else {
      icon = Icons.help_outline;
      color = Colors.grey;
      text = status.toString();
    }

    return Row(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(width: 8),
        Text(
          name,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        const Spacer(),
        Text(
          text,
          style: TextStyle(color: color, fontSize: 12),
        ),
      ],
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    // Don't use IndexedStack - rebuild the screen to avoid duplicate players
    final currentScreen = _getCurrentScreen();

    return Scaffold(
      body: currentScreen,
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 20,
              offset: const Offset(0, -5),
            ),
          ],
        ),
        child: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: (index) => setState(() => _currentIndex = index),
          backgroundColor: Colors.white,
          elevation: 0,
          type: BottomNavigationBarType.fixed,
          selectedItemColor: const Color(0xFF6366F1),
          unselectedItemColor: Colors.grey.shade400,
          selectedLabelStyle: const TextStyle(
            fontWeight: FontWeight.w600,
            fontSize: 12,
          ),
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.folder_outlined),
              activeIcon: Icon(Icons.folder),
              label: 'Home',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.favorite_outline),
              activeIcon: Icon(Icons.favorite),
              label: 'Favorites',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.settings_outlined),
              activeIcon: Icon(Icons.settings),
              label: 'Settings',
            ),
          ],
        ),
      ),
    );
  }

  Widget _getCurrentScreen() {
    switch (_currentIndex) {
      case 0:
        return const lib_screens.HomeScreen();
      case 1:
        return const FavoritesScreen();
      case 2:
        return const SettingsScreen();
      default:
        return const lib_screens.HomeScreen();
    }
  }
}
