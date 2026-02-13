import 'package:flutter/material.dart';

class FavoritesEmptyState extends StatelessWidget {
  final VoidCallback? onTryDemo;

  const FavoritesEmptyState({super.key, this.onTryDemo});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.favorite_border, size: 80, color: Colors.grey.shade400),
            const SizedBox(height: 20),
            Text('No favorite videos',
                style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Colors.grey.shade700)),
            const SizedBox(height: 8),
            Text('Long press a video to add it to favorites',
                style: TextStyle(fontSize: 14, color: Colors.grey.shade500)),
            if (onTryDemo != null) ...[
              const SizedBox(height: 32),
              ElevatedButton.icon(
                onPressed: onTryDemo,
                icon: const Icon(Icons.play_circle_outline),
                label: const Text('Try Demo Mode'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF6366F1),
                  foregroundColor: Colors.white,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
