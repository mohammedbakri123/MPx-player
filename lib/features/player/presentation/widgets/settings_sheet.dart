import 'package:flutter/material.dart';
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
      decoration: BoxDecoration(
        color: Colors.grey.shade900,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
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
            
            // Volume Slider
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  const Icon(Icons.volume_down, color: Colors.white, size: 20),
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
                  const Icon(Icons.volume_up, color: Colors.white, size: 20),
                ],
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
