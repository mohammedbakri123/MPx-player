import 'package:flutter/material.dart';
import 'favorites_title.dart';
import 'favorites_icon_button.dart';

class FavoritesHeader extends StatelessWidget {
  final int videoCount;
  final VoidCallback? onFilterTap;

  const FavoritesHeader({
    super.key,
    required this.videoCount,
    this.onFilterTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
      child: Row(
        children: [
          _buildIcon(),
          const SizedBox(width: 16),
          Expanded(child: FavoritesTitle(videoCount: videoCount)),
          FavoritesIconButton(
              icon: Icons.filter_list, onTap: onFilterTap ?? () {}),
        ],
      ),
    );
  }

  Widget _buildIcon() {
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Icon(Icons.favorite, color: Colors.red.shade500, size: 24),
    );
  }
}
