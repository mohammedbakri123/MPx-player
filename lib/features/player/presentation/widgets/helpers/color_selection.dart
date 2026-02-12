import 'package:flutter/material.dart';
import '../../../controller/player_controller.dart';

class ColorSelection extends StatelessWidget {
  final PlayerController controller;
  final VoidCallback onChanged;

  const ColorSelection({
    super.key,
    required this.controller,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
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
              _ColorOption(
                color: Colors.white,
                name: 'White',
                isSelected: controller.subtitleColor == Colors.white,
                onTap: () {
                  controller.setSubtitleColor(Colors.white);
                  onChanged();
                },
              ),
              _ColorOption(
                color: Colors.yellow,
                name: 'Yellow',
                isSelected: controller.subtitleColor == Colors.yellow,
                onTap: () {
                  controller.setSubtitleColor(Colors.yellow);
                  onChanged();
                },
              ),
              _ColorOption(
                color: Colors.cyan,
                name: 'Cyan',
                isSelected: controller.subtitleColor == Colors.cyan,
                onTap: () {
                  controller.setSubtitleColor(Colors.cyan);
                  onChanged();
                },
              ),
              _ColorOption(
                color: Colors.green,
                name: 'Green',
                isSelected: controller.subtitleColor == Colors.green,
                onTap: () {
                  controller.setSubtitleColor(Colors.green);
                  onChanged();
                },
              ),
              _ColorOption(
                color: Colors.red,
                name: 'Red',
                isSelected: controller.subtitleColor == Colors.red,
                onTap: () {
                  controller.setSubtitleColor(Colors.red);
                  onChanged();
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ColorOption extends StatelessWidget {
  final Color color;
  final String name;
  final bool isSelected;
  final VoidCallback onTap;

  const _ColorOption({
    required this.color,
    required this.name,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
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
}
