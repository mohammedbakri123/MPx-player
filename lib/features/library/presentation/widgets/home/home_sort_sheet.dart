import 'package:flutter/material.dart';
import '../../../controller/file_browser_controller.dart';

void showHomeSortSheet(BuildContext context, FileBrowserController controller) {
  showModalBottomSheet(
    context: context,
    backgroundColor: Colors.transparent,
    builder: (context) => Container(
      decoration: const BoxDecoration(
        color: Color(0xFFF8FAFC),
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
                margin: const EdgeInsets.only(top: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2))),
            const Padding(
                padding: EdgeInsets.all(16),
                child: Text('Sort By',
                    style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF0F172A)))),
            SortTile(
                icon: Icons.sort_by_alpha_rounded,
                label: 'Name',
                selected: controller.sortBy == SortBy.name,
                ascending: controller.sortOrder == SortOrder.ascending,
                onTap: () {
                  controller.setSortBy(SortBy.name);
                  Navigator.pop(context);
                }),
            SortTile(
                icon: Icons.schedule_rounded,
                label: 'Date',
                selected: controller.sortBy == SortBy.date,
                ascending: controller.sortOrder == SortOrder.ascending,
                onTap: () {
                  controller.setSortBy(SortBy.date);
                  Navigator.pop(context);
                }),
            SortTile(
                icon: Icons.data_usage_rounded,
                label: 'Size',
                selected: controller.sortBy == SortBy.size,
                ascending: controller.sortOrder == SortOrder.ascending,
                onTap: () {
                  controller.setSortBy(SortBy.size);
                  Navigator.pop(context);
                }),
            SortTile(
                icon: Icons.video_library_outlined,
                label: 'Video Count',
                selected: controller.sortBy == SortBy.videos,
                ascending: controller.sortOrder == SortOrder.ascending,
                onTap: () {
                  controller.setSortBy(SortBy.videos);
                  Navigator.pop(context);
                }),
            const SizedBox(height: 12),
          ],
        ),
      ),
    ),
  );
}

class SortTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final bool ascending;
  final VoidCallback onTap;

  const SortTile({
    super.key,
    required this.icon,
    required this.label,
    required this.selected,
    required this.ascending,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => ListTile(
        leading: Icon(icon, color: const Color(0xFF334155), size: 20),
        title: Text(label,
            style: const TextStyle(
                color: Color(0xFF0F172A),
                fontWeight: FontWeight.w600,
                fontSize: 14)),
        trailing: selected
            ? Icon(
                ascending
                    ? Icons.arrow_upward_rounded
                    : Icons.arrow_downward_rounded,
                size: 18,
                color: const Color(0xFF2563EB))
            : null,
        onTap: onTap,
      );
}
