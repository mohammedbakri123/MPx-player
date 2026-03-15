import 'package:flutter/material.dart';

class PlayerVolumeIndicator extends StatelessWidget {
  final bool isMuted;

  const PlayerVolumeIndicator({super.key, required this.isMuted});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Icon(
        isMuted ? Icons.volume_off : Icons.volume_up,
        color: Colors.white,
        size: 40,
      ),
    );
  }
}
