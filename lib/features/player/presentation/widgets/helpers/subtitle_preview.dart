import 'package:flutter/material.dart';
import '../../../controller/player_controller.dart';

class SubtitlePreview extends StatelessWidget {
  final PlayerController controller;

  const SubtitlePreview({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
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
          color: controller.subtitleColor,
          fontSize: controller.subtitleFontSize,
          backgroundColor: controller.subtitleHasBackground
              ? Colors.black.withValues(alpha: 0.7)
              : Colors.transparent,
        ),
      ),
    );
  }
}
