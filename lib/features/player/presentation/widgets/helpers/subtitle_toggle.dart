import 'package:flutter/material.dart';
import '../../../controller/player_controller.dart';

class SubtitleToggle extends StatelessWidget {
  final PlayerController controller;
  final VoidCallback onChanged;

  const SubtitleToggle({
    super.key,
    required this.controller,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title:
          const Text('Enable Subtitles', style: TextStyle(color: Colors.white)),
      subtitle: const Text(
        'Subtitles will auto-detect',
        style: TextStyle(color: Colors.grey, fontSize: 12),
      ),
      value: controller.subtitlesEnabled,
      onChanged: (value) {
        controller.toggleSubtitles(value);
        onChanged();
      },
    );
  }
}
