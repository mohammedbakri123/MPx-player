import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../../library/domain/entities/video_file.dart';
import '../../controller/player_controller.dart';
import '../../data/repositories/media_kit_player_repository.dart';
import '../widgets/player_view.dart';

/// Wrapper widget that provides PlayerController using Provider.
///
/// This widget creates a PlayerController with proper dependency injection
/// and automatically handles disposal when the widget is removed.
class VideoPlayerScreen extends StatelessWidget {
  final VideoFile video;

  const VideoPlayerScreen({super.key, required this.video});

  @override
  Widget build(BuildContext context) {
    // Provide PlayerController using ChangeNotifierProvider
    // Provider automatically handles disposal via controller.dispose()
    return ChangeNotifierProvider(
      create: (_) {
        final repository = MediaKitPlayerRepository();
        final controller = PlayerController(repository);
        controller.loadVideo(video.path);
        WakelockPlus.enable();
        controller.startHideTimer();
        return controller;
      },
      child: _VideoPlayerScreenContent(video: video),
    );
  }
}

/// The actual screen content that consumes PlayerController from Provider.
class _VideoPlayerScreenContent extends StatelessWidget {
  final VideoFile video;

  const _VideoPlayerScreenContent({required this.video});

  @override
  Widget build(BuildContext context) {
    // Get controller from Provider
    final controller = context.watch<PlayerController>();

    // Handle fullscreen state
    if (controller.isFullscreen) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
      SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    } else {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: PlayerView(
        controller: controller,
        videoTitle: video.title,
        onBack: () => Navigator.pop(context),
        onSubtitleSettings: () => _showSubtitleSettings(context, controller),
        onSettings: () => _showSettings(context, controller),
      ),
    );
  }

  void _showSubtitleSettings(
      BuildContext context, PlayerController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => _SubtitleSettingsSheet(controller: controller),
    );
  }

  void _showSettings(BuildContext context, PlayerController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => _SettingsSheet(controller: controller),
    );
  }
}

// Subtitle Settings Bottom Sheet
class _SubtitleSettingsSheet extends StatefulWidget {
  final PlayerController controller;

  const _SubtitleSettingsSheet({required this.controller});

  @override
  State<_SubtitleSettingsSheet> createState() => _SubtitleSettingsSheetState();
}

class _SubtitleSettingsSheetState extends State<_SubtitleSettingsSheet> {
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade900,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildHandle(),
              _buildTitle('Subtitle Settings'),
              _buildSubtitleToggle(),
              if (widget.controller.subtitlesEnabled) ...[
                const Divider(color: Colors.grey),
                _buildFontSizeControl(),
                _buildColorSelection(),
                _buildBackgroundToggle(),
                _buildPreview(),
              ],
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHandle() {
    return Container(
      margin: const EdgeInsets.only(top: 8),
      width: 40,
      height: 4,
      decoration: BoxDecoration(
        color: Colors.grey.shade600,
        borderRadius: BorderRadius.circular(2),
      ),
    );
  }

  Widget _buildTitle(String title) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Text(
        title,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildSubtitleToggle() {
    return SwitchListTile(
      title:
          const Text('Enable Subtitles', style: TextStyle(color: Colors.white)),
      subtitle: const Text(
        'Subtitles will auto-detect',
        style: TextStyle(color: Colors.grey, fontSize: 12),
      ),
      value: widget.controller.subtitlesEnabled,
      onChanged: (value) {
        setState(() => widget.controller.toggleSubtitles(value));
      },
    );
  }

  Widget _buildFontSizeControl() {
    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text('Font Size',
              style: TextStyle(color: Colors.white, fontSize: 16)),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              const Text('A',
                  style: TextStyle(color: Colors.grey, fontSize: 12)),
              Expanded(
                child: Slider(
                  value: widget.controller.subtitleFontSize,
                  min: 12,
                  max: 48,
                  divisions: 12,
                  onChanged: (value) {
                    setState(
                        () => widget.controller.setSubtitleFontSize(value));
                  },
                ),
              ),
              const Text('A',
                  style: TextStyle(color: Colors.grey, fontSize: 20)),
            ],
          ),
        ),
        Center(
          child: Text(
            '${widget.controller.subtitleFontSize.toInt()}pt',
            style: const TextStyle(color: Colors.grey),
          ),
        ),
      ],
    );
  }

  Widget _buildColorSelection() {
    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text('Text Color',
              style: TextStyle(color: Colors.white, fontSize: 16)),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            spacing: 12,
            children: [
              _colorOption(Colors.white, 'White'),
              _colorOption(Colors.yellow, 'Yellow'),
              _colorOption(Colors.cyan, 'Cyan'),
              _colorOption(Colors.green, 'Green'),
              _colorOption(Colors.red, 'Red'),
            ],
          ),
        ),
      ],
    );
  }

  Widget _colorOption(Color color, String name) {
    final isSelected = widget.controller.subtitleColor.value == color.value;
    return GestureDetector(
      onTap: () => setState(() => widget.controller.setSubtitleColor(color)),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? Colors.blue : Colors.grey.shade800,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 16,
              height: 16,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 6),
            Text(name,
                style:
                    TextStyle(color: isSelected ? Colors.white : Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildBackgroundToggle() {
    return SwitchListTile(
      title:
          const Text('Text Background', style: TextStyle(color: Colors.white)),
      value: widget.controller.subtitleHasBackground,
      onChanged: (value) {
        setState(() => widget.controller.setSubtitleBackground(value));
      },
    );
  }

  Widget _buildPreview() {
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.black,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        'This is a subtitle preview',
        style: TextStyle(
          color: widget.controller.subtitleColor,
          fontSize: widget.controller.subtitleFontSize,
          backgroundColor: widget.controller.subtitleHasBackground
              ? Colors.black.withOpacity(0.7)
              : Colors.transparent,
        ),
      ),
    );
  }
}

// Settings Bottom Sheet
class _SettingsSheet extends StatelessWidget {
  final PlayerController controller;

  const _SettingsSheet({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade900,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.only(top: 8),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade600,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Settings',
                style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.speed, color: Colors.white),
              title: const Text('Speed', style: TextStyle(color: Colors.white)),
              trailing: Text('${controller.playbackSpeed}x',
                  style: const TextStyle(color: Colors.blue)),
              onTap: () {
                Navigator.pop(context);
                controller.changeSpeed();
              },
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}
