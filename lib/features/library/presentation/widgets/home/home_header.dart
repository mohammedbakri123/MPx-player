import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../controller/file_browser_controller.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback? onSearchTap;

  const HomeHeader({
    super.key,
    this.onSearchTap,
  });

  @override
  Widget build(BuildContext context) {
    return Consumer<FileBrowserController>(
      builder: (context, controller, _) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
          child: Row(
            children: [
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
              _IconButton(
                icon: controller.isGridView ? Icons.list : Icons.grid_view,
                onTap: controller.toggleViewMode,
              ),
              const SizedBox(width: 8),
              PopupMenuButton<String>(
                onSelected: (value) {
                  switch (value) {
                    case 'name':
                      controller.setSortBy(SortBy.name);
                      break;
                    case 'date':
                      controller.setSortBy(SortBy.date);
                      break;
                    case 'size':
                      controller.setSortBy(SortBy.size);
                      break;
                  }
                },
                offset: const Offset(0, 50),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'name',
                    child: Row(
                      children: [
                        const Icon(Icons.sort_by_alpha, size: 20),
                        const SizedBox(width: 12),
                        const Text('Name'),
                        if (controller.sortBy == SortBy.name) ...[
                          const Spacer(),
                          Icon(
                            controller.sortOrder == SortOrder.ascending
                                ? Icons.arrow_upward
                                : Icons.arrow_downward,
                            size: 16,
                          ),
                        ],
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'date',
                    child: Row(
                      children: [
                        const Icon(Icons.calendar_today, size: 20),
                        const SizedBox(width: 12),
                        const Text('Date'),
                        if (controller.sortBy == SortBy.date) ...[
                          const Spacer(),
                          Icon(
                            controller.sortOrder == SortOrder.ascending
                                ? Icons.arrow_upward
                                : Icons.arrow_downward,
                            size: 16,
                          ),
                        ],
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'size',
                    child: Row(
                      children: [
                        const Icon(Icons.data_usage, size: 20),
                        const SizedBox(width: 12),
                        const Text('Size'),
                        if (controller.sortBy == SortBy.size) ...[
                          const Spacer(),
                          Icon(
                            controller.sortOrder == SortOrder.ascending
                                ? Icons.arrow_upward
                                : Icons.arrow_downward,
                            size: 16,
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
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
                  child:
                      Icon(Icons.sort, color: Colors.grey.shade600, size: 22),
                ),
              ),
              const SizedBox(width: 8),
              _IconButton(
                icon: Icons.search,
                onTap: onSearchTap ?? () {},
              ),
            ],
          ),
        );
      },
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
