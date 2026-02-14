import 'package:flutter/material.dart';

class HomeHeader extends StatelessWidget {
  const HomeHeader({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
      child: Row(
        children: [
          // Container(
          //   width: 48,
          //   height: 48,
          //   decoration: BoxDecoration(
          //     color: const Color(0xFF6366F1).withValues(alpha: 0.1),
          //     borderRadius: BorderRadius.circular(16),
          //   ),
          //   child: const Icon(
          //     Icons.menu,
          //     color: Color(0xFF6366F1),
          //     size: 24,
          //   ),
          // ),
          const SizedBox(width: 16),
          const Expanded(
            child: Text(
              'MPx Player',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                letterSpacing: -0.5,
              ),
            ),
          ),
          _IconButton(icon: Icons.search, onTap: () {}),
          const SizedBox(width: 12),
        ],
      ),
    );
  }
}

class _IconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _IconButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 10,
              offset: const Offset(0, 2),
            ),
          ],
          border: Border.all(color: Colors.grey.shade200),
        ),
        child: Icon(icon, color: Colors.grey.shade600, size: 22),
      ),
    );
  }
}
