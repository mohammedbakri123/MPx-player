import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controller/player_controller.dart';
import 'helpers/bottom_sheet_handle.dart';

class SettingsSheet extends StatelessWidget {
  final PlayerController controller;

  const SettingsSheet({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF101010),
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'Playback Settings',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
            Consumer<PlayerController>(
              builder: (context, controller, _) => Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  children: [
                    const Icon(
                      Icons.volume_down,
                      color: Colors.white,
                      size: 20,
                    ),
                    Expanded(
                      child: Slider(
                        value: controller.volume,
                        min: 0,
                        max: 100,
                        activeColor: Colors.blue,
                        inactiveColor: Colors.grey.shade700,
                        onChanged: (value) {
                          controller.setVolume(value);
                        },
                      ),
                    ),
                    const Icon(
                      Icons.volume_up,
                      color: Colors.white70,
                      size: 20,
                    ),
                  ],
                ),
              ),
            ),
            const Divider(color: Colors.white10, height: 1),
            ListTile(
              leading: const Icon(Icons.speed, color: Colors.white),
              title: const Text(
                'Playback speed',
                style: TextStyle(color: Colors.white),
              ),
              subtitle: const Text(
                'Fine-tune how fast videos play',
                style: TextStyle(color: Colors.grey, fontSize: 12),
              ),
              trailing: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  '${controller.playbackSpeed}x',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              onTap: () {
                showModalBottomSheet(
                  context: context,
                  backgroundColor: Colors.transparent,
                  builder: (ctx) => _SettingsSpeedSheet(
                    currentSpeed: controller.playbackSpeed,
                    onSpeedSelected: (speed) {
                      controller.setSpeed(speed);
                    },
                  ),
                );
              },
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}

class _SettingsSpeedSheet extends StatelessWidget {
  final double currentSpeed;
  final ValueChanged<double> onSpeedSelected;

  const _SettingsSpeedSheet({
    required this.currentSpeed,
    required this.onSpeedSelected,
  });

  static const List<double> speeds = [
    0.25,
    0.5,
    0.75,
    1.0,
    1.25,
    1.5,
    1.75,
    2.0,
    2.5,
    3.0
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Playback Speed',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${currentSpeed}x',
                    style: const TextStyle(
                      color: Colors.blue,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const Divider(color: Colors.grey, height: 1),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 300),
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: speeds.length,
                itemBuilder: (context, index) {
                  final speed = speeds[index];
                  final isSelected = speed == currentSpeed;

                  return ListTile(
                    title: Text(
                      speed == 1.0 ? 'Normal' : '${speed}x',
                      style: TextStyle(
                        color: isSelected ? Colors.blue : Colors.white,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w600 : FontWeight.normal,
                      ),
                    ),
                    trailing: isSelected
                        ? const Icon(Icons.check, color: Colors.blue)
                        : null,
                    onTap: () {
                      onSpeedSelected(speed);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
