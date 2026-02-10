import 'package:flutter/material.dart';
import '../../controller/library_controller.dart';

class HomeSectionHeader extends StatelessWidget {
  final LibraryController controller;

  const HomeSectionHeader({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        children: [
          Text(
            'Storage Directories',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Colors.grey.shade500,
              letterSpacing: 0.5,
            ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: Colors.grey.shade200,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                _ViewButton(
                  icon: Icons.view_list,
                  isSelected: !controller.isGridView,
                  onTap: () => controller.setViewMode(false),
                ),
                _ViewButton(
                  icon: Icons.grid_view,
                  isSelected: controller.isGridView,
                  onTap: () => controller.setViewMode(true),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ViewButton extends StatelessWidget {
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _ViewButton({
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(6),
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.05),
                    blurRadius: 4,
                  ),
                ]
              : null,
        ),
        child: Icon(
          icon,
          size: 16,
          color: isSelected ? const Color(0xFF6366F1) : Colors.grey.shade500,
        ),
      ),
    );
  }
}
