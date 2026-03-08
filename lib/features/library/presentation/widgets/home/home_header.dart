import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../controller/file_browser_controller.dart';

class HomeHeader extends StatelessWidget {
  final VoidCallback? onSearchTap;

  const HomeHeader({
    super.key,
    this.onSearchTap,
  });

  String _sortLabel(SortBy sortBy) {
    switch (sortBy) {
      case SortBy.name:
        return 'Name';
      case SortBy.date:
        return 'Date';
      case SortBy.size:
        return 'Size';
      case SortBy.videos:
        return 'Videos';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<FileBrowserController>(
      builder: (context, controller, _) {
        final items = controller.filteredItems;
        final folderCount = items.where((item) => item.isDirectory).length;
        final videoCount = items.where((item) => item.isVideo).length;

        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
                  ),
                  borderRadius: BorderRadius.circular(28),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF0F172A).withValues(alpha: 0.16),
                      blurRadius: 24,
                      offset: const Offset(0, 12),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                controller.canGoBack
                                    ? controller.currentFolderName
                                    : 'Your Library',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 26,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -0.8,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                controller.canGoBack
                                    ? 'Browse folders, open videos, and keep your watch flow moving.'
                                    : 'Jump back into your videos with a cleaner, faster library.',
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.74),
                                  fontSize: 13,
                                  height: 1.35,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        _HeaderIconButton(
                          icon: controller.isGridView
                              ? Icons.view_list_rounded
                              : Icons.grid_view_rounded,
                          onTap: controller.toggleViewMode,
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: _MetricPill(
                            label: 'Folders',
                            value: '$folderCount',
                            icon: Icons.folder_copy_outlined,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _MetricPill(
                            label: 'Videos',
                            value: '$videoCount',
                            icon: Icons.play_circle_outline_rounded,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _MetricPill(
                            label: 'Filter',
                            value: controller.showOnlyVideos
                                ? 'Video only'
                                : 'All files',
                            icon: Icons.filter_alt_outlined,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Material(
                      color: Colors.white.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(20),
                      child: InkWell(
                        onTap: onSearchTap,
                        borderRadius: BorderRadius.circular(20),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 14,
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.search_rounded,
                                color: Colors.white.withValues(alpha: 0.82),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  'Search videos, folders, and metadata',
                                  style: TextStyle(
                                    color: Colors.white.withValues(alpha: 0.72),
                                    fontSize: 14,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                              Icon(
                                Icons.arrow_outward_rounded,
                                size: 18,
                                color: Colors.white.withValues(alpha: 0.72),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _ActionChip(
                      icon: controller.showOnlyVideos
                          ? Icons.video_collection_outlined
                          : Icons.folder_open_outlined,
                      label: controller.showOnlyVideos
                          ? 'Showing Videos'
                          : 'Showing All',
                      highlighted: controller.showOnlyVideos,
                      onTap: controller.toggleShowOnlyVideos,
                    ),
                    const SizedBox(width: 10),
                    _ActionChip(
                      icon: Icons.swap_vert_rounded,
                      label:
                          'Sort: ${_sortLabel(controller.sortBy)} ${controller.sortOrder == SortOrder.ascending ? 'Up' : 'Down'}',
                      onTap: () => _showSortSheet(context, controller),
                    ),
                    const SizedBox(width: 10),
                    _ActionChip(
                      icon: controller.isGridView
                          ? Icons.dashboard_customize_outlined
                          : Icons.view_agenda_outlined,
                      label: controller.isGridView ? 'Grid View' : 'List View',
                      onTap: controller.toggleViewMode,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showSortSheet(BuildContext context, FileBrowserController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return Container(
          decoration: const BoxDecoration(
            color: Color(0xFFF8FAFC),
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  margin: const EdgeInsets.only(top: 12),
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
                  child: Row(
                    children: [
                      Text(
                        'Sort Library',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ],
                  ),
                ),
                _SortTile(
                  icon: Icons.sort_by_alpha_rounded,
                  label: 'Name',
                  selected: controller.sortBy == SortBy.name,
                  ascending: controller.sortOrder == SortOrder.ascending,
                  onTap: () {
                    controller.setSortBy(SortBy.name);
                    Navigator.pop(context);
                  },
                ),
                _SortTile(
                  icon: Icons.schedule_rounded,
                  label: 'Date',
                  selected: controller.sortBy == SortBy.date,
                  ascending: controller.sortOrder == SortOrder.ascending,
                  onTap: () {
                    controller.setSortBy(SortBy.date);
                    Navigator.pop(context);
                  },
                ),
                _SortTile(
                  icon: Icons.data_usage_rounded,
                  label: 'Size',
                  selected: controller.sortBy == SortBy.size,
                  ascending: controller.sortOrder == SortOrder.ascending,
                  onTap: () {
                    controller.setSortBy(SortBy.size);
                    Navigator.pop(context);
                  },
                ),
                _SortTile(
                  icon: Icons.video_library_outlined,
                  label: 'Video Count',
                  selected: controller.sortBy == SortBy.videos,
                  ascending: controller.sortOrder == SortOrder.ascending,
                  onTap: () {
                    controller.setSortBy(SortBy.videos);
                    Navigator.pop(context);
                  },
                ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _HeaderIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white.withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: SizedBox(
          width: 48,
          height: 48,
          child: Icon(icon, color: Colors.white),
        ),
      ),
    );
  }
}

class _MetricPill extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;

  const _MetricPill({
    required this.label,
    required this.value,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: Colors.white.withValues(alpha: 0.9)),
          const SizedBox(height: 10),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.64),
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool highlighted;

  const _ActionChip({
    required this.icon,
    required this.label,
    required this.onTap,
    this.highlighted = false,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: highlighted ? const Color(0xFFDBEAFE) : Colors.white,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: highlighted
                  ? const Color(0xFF93C5FD)
                  : const Color(0xFFE2E8F0),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                icon,
                size: 16,
                color: highlighted
                    ? const Color(0xFF1D4ED8)
                    : const Color(0xFF475569),
              ),
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  color: highlighted
                      ? const Color(0xFF1D4ED8)
                      : const Color(0xFF0F172A),
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SortTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final bool ascending;
  final VoidCallback onTap;

  const _SortTile({
    required this.icon,
    required this.label,
    required this.selected,
    required this.ascending,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: const Color(0xFF334155)),
      title: Text(
        label,
        style: const TextStyle(
          color: Color(0xFF0F172A),
          fontWeight: FontWeight.w600,
        ),
      ),
      trailing: selected
          ? Icon(
              ascending
                  ? Icons.arrow_upward_rounded
                  : Icons.arrow_downward_rounded,
              size: 18,
              color: const Color(0xFF2563EB),
            )
          : null,
      onTap: onTap,
    );
  }
}
