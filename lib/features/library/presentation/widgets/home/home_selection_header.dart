import 'package:flutter/material.dart';
import '../../../controller/file_browser_controller.dart';

class HomeSelectionHeader extends StatelessWidget {
  final FileBrowserController controller;
  final VoidCallback onClose;
  final VoidCallback onDelete;
  final VoidCallback onSelectAll;

  const HomeSelectionHeader({
    super.key,
    required this.controller,
    required this.onClose,
    required this.onDelete,
    required this.onSelectAll,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.05),
                blurRadius: 18,
                offset: const Offset(0, 8))
          ],
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Row(
          children: [
            SelectionActionButton(
                icon: Icons.close, onTap: onClose),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${controller.selectedCount} selected',
                      style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                          color: Color(0xFF0F172A))),
                  const SizedBox(height: 2),
                  const Text('Select more items or manage them in one go.',
                      style: TextStyle(
                          color: Color(0xFF64748B),
                          fontSize: 12,
                          fontWeight: FontWeight.w500)),
                ],
              ),
            ),
            SelectionActionButton(
              icon: Icons.delete_outline,
              tint: const Color(0xFFDC2626),
              background: const Color(0xFFFEE2E2),
              onTap: onDelete,
            ),
            const SizedBox(width: 8),
            SelectionActionButton(
                icon: Icons.select_all, onTap: onSelectAll),
          ],
        ),
      ),
    );
  }
}

class SelectionActionButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final Color tint;
  final Color background;

  const SelectionActionButton({
    super.key,
    required this.icon,
    required this.onTap,
    this.tint = const Color(0xFF475569),
    this.background = Colors.white,
  });

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFFE2E8F0))),
          child: Icon(icon, color: tint, size: 22),
        ),
      );
}
