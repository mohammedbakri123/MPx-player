import 'package:flutter/material.dart';
import '../../../controller/player_controller.dart';

class BackgroundToggle extends StatelessWidget {
  final PlayerController controller;
  final VoidCallback onChanged;

  const BackgroundToggle({
    super.key,
    required this.controller,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title:
          const Text('Text Background', style: TextStyle(color: Colors.white)),
      value: controller.subtitleHasBackground,
      onChanged: (value) {
        controller.setSubtitleBackground(value);
        onChanged();
      },
    );
  }
}
