import 'package:flutter/material.dart';
import '../../models/folder_item.dart';

class BreadcrumbBar extends StatelessWidget {
  const BreadcrumbBar({
    super.key,
    required this.breadcrumbs,
    required this.onSelect,
    required this.onBack,
  });

  final List<FolderItem> breadcrumbs;
  final ValueChanged<FolderItem?> onSelect;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.35),
        border: Border(
          bottom: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: 0.4),
          ),
        ),
      ),
      child: Row(
        children: <Widget>[
          if (breadcrumbs.isNotEmpty) ...<Widget>[
            IconButton(
              icon: const Icon(Icons.arrow_back_rounded, size: 20),
              tooltip: 'Back',
              visualDensity: VisualDensity.compact,
              onPressed: onBack,
            ),
            const SizedBox(width: 4),
          ],
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: <Widget>[
                  InkWell(
                    borderRadius: BorderRadius.circular(6),
                    onTap: () => onSelect(null),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: <Widget>[
                          Icon(
                            Icons.home_rounded,
                            size: 18,
                            color: breadcrumbs.isEmpty
                                ? scheme.primary
                                : scheme.onSurfaceVariant,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            'Library',
                            style: TextStyle(
                              fontWeight: breadcrumbs.isEmpty
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                              color: breadcrumbs.isEmpty
                                  ? scheme.primary
                                  : scheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  for (int i = 0; i < breadcrumbs.length; i++) ...<Widget>[
                    Icon(
                      Icons.chevron_right_rounded,
                      size: 18,
                      color: scheme.outline,
                    ),
                    InkWell(
                      borderRadius: BorderRadius.circular(6),
                      onTap: () => onSelect(breadcrumbs[i]),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        child: Text(
                          breadcrumbs[i].name,
                          style: TextStyle(
                            fontWeight: i == breadcrumbs.length - 1
                                ? FontWeight.bold
                                : FontWeight.normal,
                            color: i == breadcrumbs.length - 1
                                ? scheme.primary
                                : scheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
