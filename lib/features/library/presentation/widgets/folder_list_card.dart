import 'package:flutter/material.dart';
import 'package:mpx/features/library/domain/entities/video_folder.dart';

class FolderListCard extends StatelessWidget {
  final VideoFolder folder;
  final VoidCallback onTap;

  const FolderListCard({
    super.key,
    required this.folder,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
          border: Border.all(color: Colors.grey.shade100),
        ),
        child: Row(
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: const Color(0xFF6366F1).withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(
                Icons.folder,
                color: Color(0xFF6366F1),
                size: 28,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    folder.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1E293B),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${folder.videoCount} Items • ${folder.formattedSize}',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: Colors.grey.shade500,
                    ),
                  ),
                ],
              ),
            ),
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: const Color(0xFF6366F1).withValues(alpha: 0.3),
                shape: BoxShape.circle,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
